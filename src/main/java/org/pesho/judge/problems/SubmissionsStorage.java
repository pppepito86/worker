package org.pesho.judge.problems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.pesho.grader.SubmissionScore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SubmissionsStorage {

	private ObjectMapper mapper = new ObjectMapper();
	
	@Value("${work.dir}")
	private String workDir;

	public synchronized File storeSubmission(String id, String name, InputStream is) {
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		submissionsDir.mkdirs();
		File submissionFile = new File(submissionDir, name);
		try {
			FileUtils.copyInputStreamToFile(is, submissionFile);
			return submissionFile;
		} catch (Exception e) {
			throw new IllegalStateException("problem copying archive", e);
		}
	}
	
	public synchronized void setStatus(String id, String status) throws IOException {
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		File statusFile = new File(submissionDir, "status");
		FileUtils.writeStringToFile(statusFile, status);
	}
	
	public synchronized String getStatus(String id) throws IOException {
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		File statusFile = new File(submissionDir, "status");
		return FileUtils.readFileToString(statusFile);
	}

	public synchronized void setResult(String id, SubmissionScore score) throws IOException {
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		File scoreFile = new File(submissionDir, "score");
		FileUtils.writeStringToFile(scoreFile, mapper.writeValueAsString(score));
	}
	
	public synchronized SubmissionScore getResult(String id) throws IOException {
		File submissionsDir = new File(workDir, "submissions");
		File submissionDir = new File(submissionsDir, id);
		File scoreFile = new File(submissionDir, "score");
		String score = FileUtils.readFileToString(scoreFile);
		return mapper.readValue(score, SubmissionScore.class);
	}
	
}
