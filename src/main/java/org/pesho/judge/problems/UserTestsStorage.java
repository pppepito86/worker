package org.pesho.judge.problems;

import java.io.File;
import java.io.IOException;
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

	private ObjectMapper mapper = new ObjectMapper();
	
	@Value("${work.dir}")
	private String workDir;

	public synchronized Map<String, List<File>> storeUserTest(String id, MultipartFile submission, List<MultipartFile> inputs, List<MultipartFile> outputs) {
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

	public synchronized void setResult(String id, SubmissionScore score) throws IOException {
		File submissionsDir = new File(workDir, "user_tests");
		File submissionDir = new File(submissionsDir, id);
		File scoreFile = new File(submissionDir, "score");
		if (score != null) {
			FileUtils.writeStringToFile(scoreFile, mapper.writeValueAsString(score));
		} else {
			scoreFile.delete();
		}
	}
	
	public synchronized SubmissionScore getResult(String id) throws IOException {
		File submissionsDir = new File(workDir, "user_tests");
		File submissionDir = new File(submissionsDir, id);
		File scoreFile = new File(submissionDir, "score");
		if (!scoreFile.exists()) return null;
		
		String score = FileUtils.readFileToString(scoreFile);
		return mapper.readValue(score, SubmissionScore.class);
	}
	
}
