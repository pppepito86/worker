package org.pesho.judge.problems;

import java.io.InputStream;
import java.util.Collection;
import java.util.Hashtable;

import org.pesho.grader.task.TaskDetails;
import org.pesho.judge.daos.ProblemDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProblemsCache {

	@Autowired
	private ProblemsStorage storage;
	
	private Hashtable<Integer, ProblemDao> cache = new Hashtable<>();
	private Hashtable<Integer, TaskDetails> cacheNew = new Hashtable<>();
	
	public ProblemDao getProblem(int id) {
		return cache.getOrDefault(id, storage.loadProblem(id));
	}
	
	public TaskDetails getProblemNew(int id) {
		return cacheNew.get(id);
	}

	public void addProblem(int id, InputStream is) {
		TaskDetails taskTests = storage.storeProblem(id, is);
		cacheNew.put(id, taskTests);
	}
	
	public void addProblem(int id, ProblemDao problem, InputStream is) {
		storage.storeProblem(id, problem, is);
		cache.put(id, problem);
	}
	
	public Collection<ProblemDao> listProblems() {
		return cache.values();
	}
	
}
