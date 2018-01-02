package org.pesho.judge.problems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.pesho.grader.task.TaskDetails;
import org.pesho.grader.task.TaskParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class ProblemsStorage {

	@Value("${work.dir}")
	private String workDir;

	private ObjectMapper objectMapper = new ObjectMapper();

	public TaskDetails loadProblem(int id) {
		File problemsDir = new File(workDir, "problems");
		File problemDir = new File(problemsDir, String.valueOf(id));
		File problemMetadata = new File(problemDir, "metadata.json");
		if (!problemMetadata.exists())
			return null;

		try {
			return objectMapper.readValue(problemMetadata, TaskDetails.class);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot parse file");
		}
	}

	public void deleteProblem(int id) {
		File problemsDir = new File(workDir, "problems");
		File problemDir = new File(problemsDir, String.valueOf(id));
		
		if (!problemDir.exists()) {
			return;
		}
		
		try {
			FileUtils.forceDelete(problemDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public TaskDetails updateProblem(int id, InputStream is) {
		deleteProblem(id);
		return storeProblem(id, is);
	}
		
	public TaskDetails storeProblem(int id, InputStream is) {
		File problemsDir = new File(workDir, "problems");
		File problemDir = new File(problemsDir, String.valueOf(id));
		
		if (problemDir.exists()) {
			throw new IllegalStateException("Problem already exists.");
		}
		
		problemDir.mkdirs();
		
		try {
			File testsFile = new File(problemDir, "problem.zip");
			FileUtils.copyInputStreamToFile(is, testsFile);
			try (ZipFile zipFile = new ZipFile(testsFile)) {
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					File test = new File(problemDir, entry.getName());
					if (entry.isDirectory()) {
						test.mkdirs();
					} else {
						FileUtils.copyInputStreamToFile(zipFile.getInputStream(entry), test);
					}
				}
			}

			File problemMetadata = new File(problemDir, "metadata.json");
			TaskParser taskParser = new TaskParser(problemDir);
			TaskDetails taskTests = TaskDetails.create(taskParser);
			FileUtils.writeByteArrayToFile(problemMetadata, objectMapper.writeValueAsBytes(taskTests));
			return taskTests;
			
		} catch (Exception e) {
			throw new IllegalStateException("problem copying archive", e);
		}
	}
	
}
