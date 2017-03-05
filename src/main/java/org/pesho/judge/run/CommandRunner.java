package org.pesho.judge.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

public class CommandRunner {

	private String cmd;
	private String[] args;
	private long timeout;

	private Process process;
	private OutputCollector outputCollector;
	private OutputCollector errorCollector;
	private Timer timer;
	
	public CommandRunner(String cmd, String[] args, long timeout) {
		this.cmd = cmd;
		if (args == null) {
			args = new String[] {};
		}
		this.args = args;
		this.timeout = timeout;
	}
	
	public int run() throws IOException, InterruptedException {
		start();
		return waitFor();
	}

	public void start() throws IOException {
		String[] command = new String[args.length + 1];
		command[0] = cmd;
		System.arraycopy(args, 0, command, 1, args.length);
		process = Runtime.getRuntime().exec(command);
		outputCollector = stream(process.getInputStream());
		errorCollector = stream(process.getErrorStream());
		timer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				kill();
			}
		};
		timer.schedule(task, timeout);
	}

	public int waitFor() throws InterruptedException {
		outputCollector.join();
		errorCollector.join();
		int exitCode = process.waitFor();
		timer.cancel();
		return exitCode;
	}

	public void kill() {
		process.destroyForcibly();
	}
	
	public String getOutput() {
		return outputCollector.getOutput();
	}

	public String getError() {
		return errorCollector.getOutput();
	}

	private OutputCollector stream(InputStream is) {
		OutputCollector collector = new OutputCollector(is);
		collector.start();
		return collector;
	}

	class OutputCollector extends Thread {

		private InputStream is;
		private StringBuffer buffer = new StringBuffer();

		public OutputCollector(InputStream is) {
			this.is = is;
		}

		@Override
		public void run() {
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				for (String line = br.readLine(); line != null; line = br.readLine()) {
					buffer.append(line + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public String getOutput() {
			return buffer.toString();
		}

	}

}
