package org.pesho.judge.problems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.pesho.grader.SubmissionScore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SubmissionsStorage {

	private Map<String, String> workerSubmissionsIds;
	private Map<String, Timestamp> submissionsUpdateTimes;
	private Map<String, Timestamp> blockedSubmissions;

	private ObjectMapper mapper = new ObjectMapper();
	
	@Value("${work.dir}")
	private String workDir;

	private String getSubmissionId (String workerSubmissionId) {
		String[] parts = workerSubmissionId.split("_");
		String submissionId = parts[0];
		for (int i = 1; i < parts.length-1; i++) {
			submissionId += "_" + parts[i];
		}
		return submissionId;
	}

	public SubmissionsStorage () {
		File submissionsDir = new File(workDir, "submissions");
		File[] submissionsDirs = submissionsDir.listFiles();
		if (submissionsDirs == null) return ;
		
		workerSubmissionsIds = new HashMap<>();
		Map<String, Long> lastModified = new HashMap<>();
		for (File submissionDir: submissionsDir.listFiles()) {
			if (!submissionDir.isDirectory()) continue;
			
			String workerSubmissionId = submissionDir.getName();
			String submissionId = getSubmissionId(workerSubmissionId);
			if (!lastModified.containsKey(submissionId) || lastModified.get(submissionId) < submissionDir.lastModified()) {
				workerSubmissionsIds.put(submissionId, workerSubmissionId);
				lastModified.put(submissionId, submissionDir.lastModified());
			}
		}

		submissionsUpdateTimes = new HashMap<>();
		blockedSubmissions = new HashMap<>();
	}

	public synchronized File storeSubmission(String submissionId, String id, String name, InputStream is, Timestamp updateTime) {
		workerSubmissionsIds.put(submissionId, id);
		submissionsUpdateTimes.put(submissionId, updateTime);
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		submissionsDir.mkdirs();
		File submissionFile = new File(submissionDir, name);
		try {
			FileUtils.copyInputStreamToFile(is, submissionFile);
			return submissionFile;
		} catch (Exception e) {
			throw new IllegalStateException("problem copying submission", e);
		}
	}

	public synchronized void block (String submissionId, Timestamp updateTime) {
		blockedSubmissions.put(submissionId, updateTime);
	}

	public synchronized void removeBlock (String submissionId) {
		blockedSubmissions.remove(submissionId);
	}

	public synchronized boolean checkBlocked (String id) {
		String submissionId = getSubmissionId(id);
		if (blockedSubmissions.containsKey(submissionId) && 
			(!submissionsUpdateTimes.containsKey(submissionId) || submissionsUpdateTimes.get(submissionId).before(blockedSubmissions.get(submissionId)))) {
			System.out.println("Blocked submission " + id + " from further grading");
			return true;
		}
		return false;
	}

	public synchronized Timestamp getUpdateTime (String id) {
		String submissionId = getSubmissionId(id);
		return submissionsUpdateTimes.get(submissionId);
	}

	public synchronized boolean setResult(String id, SubmissionScore score) {
		boolean blocked = checkBlocked(id);
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		File scoreFile = new File(submissionDir, "score");
		if (score == null) {
			scoreFile.delete();
			return blocked;
		}
		
		if (blocked == true) score.addFinalScore(-1, true);
		
		try {
			FileUtils.writeStringToFile(scoreFile, mapper.writeValueAsString(score));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Updating score for submission " + " " + id + " failed");
		}
		return blocked;
	}
	
	public synchronized SubmissionScore getResult(String submissionId) throws IOException {
		String id = workerSubmissionsIds.get(submissionId);
		if (id == null) return null;
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		File scoreFile = new File(submissionDir, "score");
		if (!scoreFile.exists()) return null;
		
		String score = FileUtils.readFileToString(scoreFile);
		return mapper.readValue(score, SubmissionScore.class);
	}
	
}
