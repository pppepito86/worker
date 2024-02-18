package org.pesho.judge.problems;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.pesho.grader.compile.CppCompileStep;
import org.pesho.grader.step.StepResult;
import org.pesho.grader.step.Verdict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PiperManager {
	
	private static String PIPER_CPP = "piper.cpp";
	
	private File piperFile;

	public PiperManager(@Value("${work.dir}") String workDir, @Value("${piper.dir}") String piperDir) {
		piperFile = new File(new File(workDir, piperDir), PIPER_CPP);
		piperFile.getParentFile().mkdirs();
		
		try {
			FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream("piper.cpp"), piperFile);
			System.out.println("Piper file copy succeeded");
			buildPiper();
			System.out.println("Piper file build succeeded");
		} catch (IOException e) {
			System.out.println("Piper file copy failed");
			e.printStackTrace();
		}	
	}
	
	private void buildPiper() {
		Map<String, Double> time = new HashMap<>();
		time.put("default", 10.);
		Map<String, Integer> memory = new HashMap<>();
		memory.put("default", 128);
		CppCompileStep compile = new CppCompileStep(piperFile, null, time, memory);
		compile.execute();
		StepResult result = compile.getResult();
		if (result.getVerdict() == Verdict.OK) {
			System.out.println("Checker built successfully");
		} else {
			System.out.println("Checker build failed!");
		}
	}
	
	public File getPiperFile() {
		return piperFile;
	}
	
}
