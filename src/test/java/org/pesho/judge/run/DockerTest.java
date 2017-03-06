package org.pesho.judge.run;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;

import org.junit.Before;
import org.junit.Test;

public class DockerTest {
	
	@Before
	public void beforeMethod() throws Exception {
		assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
		int exitCode = new CommandRunner("bash", new String[] {"-c", "which docker" }, 5000).run();
		assumeThat(exitCode, is(0));
	}
	
	@Test
	public void testEcho() throws Exception {
		DockerRunner dockerRunner = new DockerRunner("echo hello", 5000);
		int exitCode = dockerRunner.run();
		assertThat(exitCode, is(0));
		assertThat(dockerRunner.getOutput(), is("hello\n"));
	}
	
	@Test
	public void testKill() throws Exception {
		long startTime = System.currentTimeMillis();
		DockerRunner runner = new DockerRunner("sleep 2", 5000);
		runner.start();
		runner.kill();
		int exitCode = runner.waitFor();
		long totalTime = System.currentTimeMillis() - startTime;
		assertThat(exitCode, not(0));
		assertThat(totalTime, is(lessThan(2000L)));
	}

}
