package org.pesho.judge.problems;

import java.io.InputStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import org.pesho.grader.task.TaskDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProblemsCache {

	private ProblemsStorage storage;
	
	private Hashtable<Integer, TaskDetails> cache = new Hashtable<>();
	
	public ProblemsCache(@Autowired ProblemsStorage storage) {
		this.storage = storage;
		
		load();
	}
	
    public void load(){
    	Map<Integer, TaskDetails> problems = storage.loadProblems();
    	System.out.println("loaded: " + problems);
    	problems.entrySet()
    	.forEach(entry -> cache.put(entry.getKey(), entry.getValue()));
    }
	
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

	public void removeProblem(int id) {
		storage.deleteProblem(id);
		cache.remove(id);
	}
	
	public String getChecksum(int id) {
		return storage.getChecksum(id);
	}
	
	public Collection<TaskDetails> listProblems() {
		return cache.values();
	}
	
}
