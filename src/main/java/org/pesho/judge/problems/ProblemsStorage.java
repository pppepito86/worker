package org.pesho.judge.problems;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.pesho.grader.compile.CppCompileStep;
import org.pesho.grader.step.StepResult;
import org.pesho.grader.step.Verdict;
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

	public Map<Integer, TaskDetails> loadProblems() {
		Map<Integer, TaskDetails> map = new HashMap<>();
		File problemsDir = new File(workDir, "problems");
		File[] problemsDirs = problemsDir.listFiles();
		if (problemsDirs == null) return map;
		
		for (File problemDir: problemsDir.listFiles()) {
			if (!problemDir.isDirectory()) continue;
			
			File problemMetadata = new File(problemDir, "metadata.json");
			if (!problemMetadata.exists()) {
				try {
					FileUtils.forceDelete(problemDir);
				}catch (IOException e) {
					e.printStackTrace();
				}
				continue;
			}

			try {
				TaskDetails details = objectMapper.readValue(problemMetadata, TaskDetails.class);
				map.put(Integer.valueOf(problemDir.getName()), details);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return map;
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

	
	public String getChecksum(int id) {
		File problemsDir = new File(workDir, "problems");
		File problemDir = new File(problemsDir, String.valueOf(id));
		File testsFile = new File(problemDir, "problem.zip");
		if (!testsFile.exists()) return null;
		
		return getChecksum(testsFile);
	}

	public String getChecksum(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			return DigestUtils.md5Hex(fis);
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
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
					if (entry.getName().contains("__MACOSX")) {
						System.out.println("Skipping MACOS system file: " + entry.getName());
						continue;
					}
					File test = new File(problemDir, entry.getName());
					if (entry.isDirectory()) {
						test.mkdirs();
					} else {
						FileUtils.copyInputStreamToFile(zipFile.getInputStream(entry), test);
					}
				}
			}

			TaskParser taskParser = new TaskParser(problemDir);
			if (taskParser.getCppChecker().exists()) {
				System.out.println("building checker for problem: " + id);
				buildChecker(taskParser.getCppChecker());
				taskParser = new TaskParser(problemDir);
			}
			
			TaskDetails taskTests = TaskDetails.create(taskParser);

			File problemMetadata = new File(problemDir, "metadata.json");
			FileUtils.writeByteArrayToFile(problemMetadata, objectMapper.writeValueAsBytes(taskTests));
			
			return taskTests;
		} catch (Exception e) {
			try {
				FileUtils.deleteDirectory(problemDir);
			} catch (IOException ex) {
				System.out.println("Adding a new problem failed and we are unable to remove the created directory.");
				ex.printStackTrace();
			}
			throw new IllegalStateException("problem copying archive", e);
		}
	}

	private void buildChecker(File cppChecker) {
		CppCompileStep compile = new CppCompileStep(cppChecker);
		compile.execute();
		StepResult result = compile.getResult();
		if (result.getVerdict() == Verdict.OK) {
			System.out.println("Checker built successfully");
		} else {
			System.out.println("Checker build failed!");
		}
	}
	
}
