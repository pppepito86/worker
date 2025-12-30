package org.pesho.judge.problems;

import java.io.InputStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;

import org.pesho.grader.task.TaskDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProblemsCache {

	private ProblemsStorage storage;
	
	private Hashtable<String, TaskDetails> cache = new Hashtable<>();
	
	public ProblemsCache(@Autowired ProblemsStorage storage) {
		this.storage = storage;
		
		load();
	}
	
	public void load(){
		Map<String, TaskDetails> problems = storage.loadProblems();
		//System.out.println("loaded: " + problems);
		problems.entrySet().forEach(entry -> cache.put(entry.getKey(), entry.getValue()));
	}

	public TaskDetails getProblem(int id, Optional<String> instanceId) {
		return cache.get(storage.getProblemDir(id, instanceId));
	}

	public void addProblem(int id, Optional<String> instanceId, InputStream is) {
		TaskDetails taskTests = storage.storeProblem(id, instanceId, is);
		cache.put(storage.getProblemDir(id, instanceId), taskTests);
	}

	public void updateProblem(int id, Optional<String> instanceId, InputStream is) {
		TaskDetails taskTests = storage.updateProblem(id, instanceId, is);
		cache.put(storage.getProblemDir(id, instanceId), taskTests);
	}

	public void removeProblem(int id, Optional<String> instanceId) {
		storage.deleteProblem(id, instanceId);
		cache.remove(storage.getProblemDir(id, instanceId));
	}
	
	public String getChecksum(int id, Optional<String> instanceId) {
		return storage.getChecksum(id, instanceId);
	}
	
	public Collection<TaskDetails> listProblems() {
		return cache.values();
	}
	
}
