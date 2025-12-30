package org.pesho.judge.problems;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

	public Map<String, TaskDetails> loadProblems() {
		Map<String, TaskDetails> map = new HashMap<>();
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
			map.put(problemDir.getName(), taskDetails);
		}
		return map;
	}

	public String getProblemDir (int problemId, Optional<String> instanceId) {
		return instanceId.isPresent() ? String.valueOf(problemId) + "_" + instanceId.get() : String.valueOf(problemId);
	}

	public File getProblemDirFile (int problemId, Optional<String> instanceId) {
		File problemsDir = new File(workDir, "problems");
		File problemDir = new File(problemsDir, getProblemDir(problemId, instanceId));
		return problemDir;
	}

	public void deleteProblem(int id, Optional<String> instanceId) {
		File problemDir = getProblemDirFile(id, instanceId);
		FileUtils.deleteQuietly(problemDir);
	}
	
	public TaskDetails updateProblem(int id, Optional<String> instanceId, InputStream is) {
		deleteProblem(id, instanceId);
		return storeProblem(id, instanceId, is);
	}

	public String getChecksum(int id, Optional<String> instanceId) {
		File problemDir = getProblemDirFile(id, instanceId);
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

	public TaskDetails storeProblem(int id, Optional<String> instanceId, InputStream is) {
		File problemDir = getProblemDirFile(id, instanceId);
		
		if (problemDir.exists()) {
			throw new IllegalStateException("Problem already exists.");
		}
		
		problemDir.mkdirs();
		
		try {
			File testsFile = new File(problemDir, "problem.zip");
			FileUtils.copyInputStreamToFile(is, testsFile);
			unzip(testsFile, problemDir);

			TaskDetails taskDetails = new TaskDetails("task", problemDir);
			boolean hasError = false;
			if (taskDetails.getCppChecker() != null) {
				System.out.println("Building checker for problem: " + id);
				String error = buildChecker(new File(taskDetails.getCppChecker()));
				if (error != null) {
					hasError = true;
					taskDetails.addError("checker_compile", error);
				}
			}
			if (taskDetails.getCppManager() != null) {
				System.out.println("Building manager for problem: " + id);
				String error = buildManager(new File(taskDetails.getCppManager()));
				if (error != null) {
					hasError = true;
					taskDetails.addError("manager_compile", error);
				}
			}
			if (hasError) FileUtils.deleteQuietly(testsFile);
			
			File problemMetadata = new File(problemDir, "metadata.json");
			FileUtils.writeByteArrayToFile(problemMetadata, objectMapper.writeValueAsBytes(taskDetails));
			
			return taskDetails;
		} catch (Exception e) {
			throw new IllegalStateException("problem copying archive", e);
		}
	}

	private String buildChecker(File cppChecker) {
		Map<String, Double> time = new HashMap<>();
		time.put("default", 10.);
		Map<String, Integer> memory = new HashMap<>();
		memory.put("default", 512);
		File parent = Optional.ofNullable(cppChecker.getParentFile()).filter(f -> f.getName().equalsIgnoreCase("checker")).orElse(null);
		CppCompileStep compile = new CppCompileStep(cppChecker, parent, time, memory);
		compile.execute();
		StepResult result = compile.getResult();
		if (result.getVerdict() == Verdict.OK) {
			System.out.println("Checker built successfully");
			return null;
		} else {
			System.out.println("Checker build failed!");
			return result.getReason();
		}
	}

	private String buildManager(File cppManager) {
		Map<String, Double> time = new HashMap<>();
		time.put("default", 10.);
		Map<String, Integer> memory = new HashMap<>();
		memory.put("default", 512);
		File parent = Optional.ofNullable(cppManager.getParentFile()).filter(f -> f.getName().equalsIgnoreCase("manager")).orElse(null);
		CppCompileStep compile = new CppCompileStep(cppManager, parent, time, memory);
		compile.execute();
		StepResult result = compile.getResult();
		if (result.getVerdict() == Verdict.OK) {
			System.out.println("Manager built successfully");
			return null;
		} else {
			System.out.println("Manager build failed!");
			return result.getReason();
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
