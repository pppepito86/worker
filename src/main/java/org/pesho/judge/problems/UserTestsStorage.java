package org.pesho.judge.problems;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.pesho.grader.SubmissionScore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class UserTestsStorage {

	private Map<String, String> workerUserTestsIds;
	private Map<String, Timestamp> userTestsUpdateTimes;
	private Map<String, Timestamp> blockedUserTests;

	private ObjectMapper mapper = new ObjectMapper();
	
	private String workDir;

	private String getUserTestId (String workerUserTestId) {
		String[] parts = workerUserTestId.split("_");
		String userTestId = parts[0];
		for (int i = 1; i < parts.length-1; i++) {
			userTestId += "_" + parts[i];
		}
		return userTestId;
	}

	public UserTestsStorage (@Value("${work.dir}") String workDir) {
		this.workDir = workDir;

		File userTestsDir = new File(workDir, "user_tests");
		File[] userTestsDirs = userTestsDir.listFiles();
		
		workerUserTestsIds = new HashMap<>();
		if (userTestsDirs != null) {
			Map<String, Long> lastModified = new HashMap<>();
			for (File userTestDir: userTestsDir.listFiles()) {
				if (!userTestDir.isDirectory()) continue;
				
				String workerUserTestId = userTestDir.getName();
				String userTestId = getUserTestId(workerUserTestId);
				if (!lastModified.containsKey(userTestId) || lastModified.get(userTestId) < userTestDir.lastModified()) {
					workerUserTestsIds.put(userTestId, workerUserTestId);
					lastModified.put(userTestId, userTestDir.lastModified());
				}
			}
		}

		userTestsUpdateTimes = new HashMap<>();
		blockedUserTests = new HashMap<>();
	}

	public synchronized Map<String, List<File>> storeUserTest(String userTestId, String id, MultipartFile submission, List<MultipartFile> inputs, List<MultipartFile> outputs, Timestamp updateTime) {
		workerUserTestsIds.put(userTestId, id);
		userTestsUpdateTimes.put(userTestId, updateTime);
		File userTestsDir = new File(workDir, "user_tests");
		File userTestDir = new File(userTestsDir, id);
		userTestDir.mkdirs();
		Map<String, List<File>> res = new HashMap<>();
		try {
			File submissionFile = new File(userTestDir, submission.getOriginalFilename());
			FileUtils.copyInputStreamToFile(submission.getInputStream(), submissionFile);
			res.put("submission", new ArrayList<>(Arrays.asList(submissionFile)));
			res.put("inputs", new ArrayList<>());
			for (MultipartFile f : inputs) {
				File file = new File(userTestDir, f.getOriginalFilename());
				FileUtils.copyInputStreamToFile(f.getInputStream(), file);
				res.get("inputs").add(file);
			}
			res.put("outputs", new ArrayList<>());
			for (MultipartFile f : outputs) {
				File file = new File(userTestDir, f.getOriginalFilename());
				FileUtils.copyInputStreamToFile(f.getInputStream(), file);
				res.get("outputs").add(file);
			}
			return res;
		} catch (Exception e) {
			throw new IllegalStateException("problem copying user test", e);
		}
	}

	public synchronized void block (String userTestId, Timestamp updateTime) {
		blockedUserTests.put(userTestId, updateTime);
	}

	public synchronized void removeBlock (String userTestId) {
		blockedUserTests.remove(userTestId);
	}

	public synchronized boolean checkBlocked (String id) {
		String userTestId = getUserTestId(id);
		if (blockedUserTests.containsKey(userTestId) && 
			(!userTestsUpdateTimes.containsKey(userTestId) || userTestsUpdateTimes.get(userTestId).before(blockedUserTests.get(userTestId)))) {
			System.out.println("Blocked user test " + id + " from further grading");
			return true;
		}
		return false;
	}

	public synchronized Timestamp getUpdateTime (String id) {
		String userTestId = getUserTestId(id);
		return userTestsUpdateTimes.get(userTestId);
	}

	public synchronized boolean setResult(String id, SubmissionScore score) {
		boolean blocked = checkBlocked(id);
		File userTestsDir = new File(workDir, "user_tests");
		File userTestDir = new File(userTestsDir, id);
		File scoreFile = new File(userTestDir, "score");
		if (score == null) {
			scoreFile.delete();
			return blocked;
		}
		
		try {
			FileUtils.writeStringToFile(scoreFile, mapper.writeValueAsString(score));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Updating score for user test " + " " + id + " failed");
		}
		return blocked;
	}
	
	public synchronized SubmissionScore getResult(String userTestId) throws IOException {
		String id = workerUserTestsIds.get(userTestId);
		if (id == null) return null;
		File userTestsDir = new File(workDir, "user_tests");
		File userTestDir = new File(userTestsDir, id);
		File scoreFile = new File(userTestDir, "score");
		if (!scoreFile.exists()) return null;
		
		String score = FileUtils.readFileToString(scoreFile);
		return mapper.readValue(score, SubmissionScore.class);
	}

	public File getUserOutputFile(String userTestId) {
		String id = workerUserTestsIds.get(userTestId);
		if (id == null) return null;
		File userTestsDir = new File(workDir, "user_tests");
		File userTestDir = new File(userTestsDir, id);
		return new File(userTestDir, "test_user_out");
	}
	
}
