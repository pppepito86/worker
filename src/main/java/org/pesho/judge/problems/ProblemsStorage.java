package org.pesho.judge.problems;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.pesho.grader.compile.CppCompileStep;
import org.pesho.grader.step.StepResult;
import org.pesho.grader.step.Verdict;
import org.pesho.grader.task.TaskDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.zeroturnaround.exec.ProcessExecutor;

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
				continue;
			}

			TaskDetails taskDetails = new TaskDetails("task", problemDir);
			map.put(Integer.valueOf(problemDir.getName()), taskDetails);
		}
		return map;
	}

	public void deleteProblem(int id) {
		File problemsDir = new File(workDir, "problems");
		File problemDir = new File(problemsDir, String.valueOf(id));
		
		FileUtils.deleteQuietly(problemDir);
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

	public static String getChecksum(File file) {
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
			unzip(testsFile, problemDir);

			TaskDetails taskDetails = new TaskDetails("task", problemDir);
			if (taskDetails.getChecker() != null && taskDetails.getChecker().toLowerCase().endsWith(".cpp")) {
				System.out.println("building checker for problem: " + id);
				buildChecker(new File(taskDetails.getChecker()));
				taskDetails = new TaskDetails("task", problemDir);
			}
			
			File problemMetadata = new File(problemDir, "metadata.json");
			FileUtils.writeByteArrayToFile(problemMetadata, objectMapper.writeValueAsBytes(taskDetails));
			
			return taskDetails;
		} catch (Exception e) {
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
	
	public static void unzip(File file, File folder) {
		try {
			new ProcessExecutor().command("unzip", file.getCanonicalPath(), "-d", folder.getCanonicalPath()).execute();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
	}
	
}
