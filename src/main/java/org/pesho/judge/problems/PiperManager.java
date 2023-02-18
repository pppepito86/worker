package org.pesho.judge.problems;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;

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
		CppCompileStep compile = new CppCompileStep(piperFile, null);
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
