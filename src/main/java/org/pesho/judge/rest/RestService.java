package org.pesho.judge.rest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

import org.pesho.grader.GradeListener;
import org.pesho.grader.SubmissionGrader;
import org.pesho.grader.SubmissionScore;
import org.pesho.grader.step.StepResult;
import org.pesho.grader.task.TaskDetails;
import org.pesho.judge.daos.SubmissionDto;
import org.pesho.judge.problems.ProblemsCache;
import org.pesho.judge.problems.SubmissionsStorage;
import org.pesho.sandbox.CommandStatus;
import org.pesho.sandbox.SandboxExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class RestService implements GradeListener {
	
	@Value("${work.dir}")
	private String workDir;

	@Autowired
	private ProblemsCache problemsCache;

	@Autowired
	private SubmissionsStorage submissionsStorage;

	@GetMapping("/health-check")
	public String healthCheck() throws Exception {
		File dir = Files.createTempDirectory("health-check").toFile();
		if (new SandboxExecutor()
				.directory(dir)
				.timeout(0.1)
				.clean(true)
				.command("echo test")
				.execute().getResult().getStatus() == CommandStatus.SUCCESS) {
			return "ok";
		} else {
			return "failed";
		}
	}

	@GetMapping("/problems")
	public Collection<TaskDetails> listProblems() {
		return problemsCache.listProblems();
	}

	@GetMapping("/problems/{problem_id}")
	public ResponseEntity<?> getProblem(@PathVariable("problem_id") int problemId,
			@RequestParam("checksum") Optional<String> checksum) {
		TaskDetails problem = problemsCache.getProblem(Integer.valueOf(problemId));
		if (problem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		if (!checksum.isPresent()) {
			return new ResponseEntity<>(problem, HttpStatus.OK);
		}
		
		String current = problemsCache.getChecksum(Integer.valueOf(problemId));
		if (checksum.get() != null && checksum.get().equals(current)) {
			return new ResponseEntity<>(problem, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PostMapping("/problems/{problem_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> addProblem(@PathVariable("problem_id") int problemId,
			@RequestPart("file") MultipartFile file) throws Exception {
		if (problemsCache.getProblem(problemId) == null) {
			problemsCache.addProblem(problemId, file.getInputStream());
			return new ResponseEntity<>(HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}
	}

	@PutMapping("/problems/{problem_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> updateProblem(@PathVariable("problem_id") int problemId,
			@RequestPart("file") MultipartFile file) throws Exception {
		if (problemsCache.getProblem(problemId) != null) {
			problemsCache.updateProblem(problemId, file.getInputStream());
			return new ResponseEntity<>(HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@DeleteMapping("/problems/{problem_id}")
	public ResponseEntity<?> deleteProblem(@PathVariable("problem_id") int problemId) throws Exception {
		if (problemsCache.getProblem(problemId) != null) {
			problemsCache.removeProblem(problemId);
			return new ResponseEntity<>(HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PostMapping("/submissions/{submission_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> addSubmission(@PathVariable("submission_id") String submissionId,
			@RequestParam("tl") Optional<Double> timeLimit,
			@RequestPart(name = "metadata") Optional<SubmissionDto> submission,
			@RequestPart("file") MultipartFile file) throws Exception {
		File submissionFile = submissionsStorage.storeSubmission(submissionId, file.getOriginalFilename(),
				file.getInputStream());
		scoreUpdated(submissionId, new SubmissionScore());

		Runnable runnable = () -> {
			try {
				TaskDetails taskTests = problemsCache.getProblem(Integer.valueOf(submission.get().getProblemId()));
				SubmissionGrader grader = timeLimit
						.map(tl -> new SubmissionGrader(submissionId, taskTests, submissionFile.getAbsolutePath(), this, tl))
						.orElse(new SubmissionGrader(submissionId, taskTests, submissionFile.getAbsolutePath(), this));
				grader.grade();
			} catch (Exception e) {
				e.printStackTrace();
				try {
					submissionsStorage.setResult(submissionId, null);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		};
		new Thread(runnable).start();
		return new ResponseEntity<>(HttpStatus.CREATED);
	}
	
	@Override
	public void addFinalScore(String verdict, double score) {
	}
	
	@Override
	public void addScoreStep(String step, StepResult result) {
	}
	
	@Override
	public void scoreUpdated(String submissionId, SubmissionScore score) {
		try {
			submissionsStorage.setResult(submissionId, score);		
		} catch (IOException e1) {
			try {
				submissionsStorage.setResult(submissionId, score);
			} catch (IOException e2) {
				System.out.println("submission " + submissionId + " failed");
			}
		}
	}

	@GetMapping("/submissions/{submission_id}/score")
	public ResponseEntity<?> getScore(@PathVariable("submission_id") String submissionId) throws Exception {
		SubmissionScore score = submissionsStorage.getResult(submissionId);
		if (score == null) return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
		return ResponseEntity.ok(score);
	}
	
}
