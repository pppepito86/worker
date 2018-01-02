package org.pesho.judge.problems;

import java.io.InputStream;
import java.util.Collection;
import java.util.Hashtable;

import org.pesho.grader.task.TaskDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProblemsCache {

	@Autowired
	private ProblemsStorage storage;
	
	private Hashtable<Integer, TaskDetails> cache = new Hashtable<>();
	
	public TaskDetails getProblem(int id) {
		return cache.get(id);
	}
	
	public void addProblem(int id, InputStream is) {
		TaskDetails taskTests = storage.storeProblem(id, is);
		cache.put(id, taskTests);
	}

	public void updateProblem(int id, InputStream is) {
		TaskDetails taskTests = storage.updateProblem(id, is);
		cache.put(id, taskTests);
	}
	
	public Collection<TaskDetails> listProblems() {
		return cache.values();
	}
	
}
