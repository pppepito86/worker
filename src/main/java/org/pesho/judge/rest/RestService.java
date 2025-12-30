package org.pesho.judge.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantLock;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

import org.pesho.grader.GradeListener;
import org.pesho.grader.SubmissionGrader;
import org.pesho.grader.SubmissionScore;
import org.pesho.grader.task.TaskDetails;
import org.pesho.judge.daos.SubmissionDto;
import org.pesho.judge.problems.ProblemsCache;
import org.pesho.judge.problems.SubmissionsStorage;
import org.pesho.judge.problems.UserTestsStorage;
import org.pesho.sandbox.CommandStatus;
import org.pesho.sandbox.SandboxExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

	@Value("${piper.dir}")
	private String piperDir;

	@Autowired
	private ProblemsCache problemsCache;

	@Autowired
	private SubmissionsStorage submissionsStorage;

	@Autowired
	private UserTestsStorage userTestsStorage;

	private final ReentrantLock lock = new ReentrantLock();
	
	@GetMapping("/health-check")
	public String healthCheck() {
		if (!lock.tryLock()) return "not-free";
		try {
			File dir = Files.createTempDirectory("health-check").toFile();
			if (new SandboxExecutor()
					.directory(dir)
					.timeout(0.1)
					.clean(true)
					.command("/bin/echo test")
					.execute().getResult().getStatus() == CommandStatus.SUCCESS) {
				return "ok";
			} else {
				return "failed";
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "failed";
		} finally {
			lock.unlock();
		}
	}

	@GetMapping("/problems")
	public Collection<TaskDetails> listProblems() {
		return problemsCache.listProblems();
	}

	@GetMapping("/problems/{problem_id}")
	public ResponseEntity<?> getProblem(@PathVariable("problem_id") int problemId,
			@RequestParam("instanceId") Optional<String> instanceId,
			@RequestParam("checksum") Optional<String> checksum) {
		TaskDetails problem = problemsCache.getProblem(Integer.valueOf(problemId), instanceId);
		if (problem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		if (!checksum.isPresent()) {
			return new ResponseEntity<>(problem, HttpStatus.OK);
		}
		
		String current = problemsCache.getChecksum(Integer.valueOf(problemId), instanceId);
		if (checksum.get() != null && checksum.get().equals(current)) {
			return new ResponseEntity<>(problem, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PostMapping("/problems/{problem_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> addProblem(@PathVariable("problem_id") int problemId,
			@RequestParam("instanceId") Optional<String> instanceId,
			@RequestPart("file") MultipartFile file) throws Exception {
		if (problemsCache.getProblem(problemId, instanceId) == null) {
			problemsCache.addProblem(problemId, instanceId, file.getInputStream());
		} else {
			problemsCache.updateProblem(problemId, instanceId, file.getInputStream());
		}
		TaskDetails problem = problemsCache.getProblem(Integer.valueOf(problemId), instanceId);
		if (problem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
		}
		Map<String, String> errors = new HashMap<>(problem.getError());
		errors.keySet().removeIf(t -> !t.equals("checker_compile") && !t.equals("manager_compile"));
		if (!errors.isEmpty()) return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@DeleteMapping("/problems/{problem_id}")
	public ResponseEntity<?> deleteProblem(@PathVariable("problem_id") int problemId,
			@RequestParam("instanceId") Optional<String> instanceId) throws Exception {
		if (problemsCache.getProblem(problemId, instanceId) != null) {
			problemsCache.removeProblem(problemId, instanceId);
			return new ResponseEntity<>(HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PostMapping("/submissions/{submission_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public synchronized ResponseEntity<?> addSubmission(@PathVariable("submission_id") String submissionId,
			@RequestParam("instanceId") Optional<String> instanceId,
			@RequestParam("isOfficial") Optional<Boolean> isOfficial,
			@RequestParam("compileTL") Optional<Double> compileTime,
			@RequestParam("compileML") Optional<Integer> compileMemory,
			@RequestParam("points") Optional<Double> points,
			@RequestPart(name = "metadata") Optional<SubmissionDto> submission,
			@RequestPart("file") MultipartFile file) throws Exception {
		File submissionFile = submissionsStorage.storeSubmission(submissionId, file.getOriginalFilename(),
				file.getInputStream());
		scoreUpdated(submissionId, new SubmissionScore("submission"));

		Runnable runnable = () -> {
			try {
				lock.lock();
				TaskDetails taskTests = problemsCache.getProblem(Integer.valueOf(submission.get().getProblemId()), instanceId);
				SubmissionGrader grader = new SubmissionGrader(submissionId, isOfficial, taskTests, submissionFile.getAbsolutePath(), this, workDir+"/"+piperDir+"/piper", compileTime, compileMemory, points);
				grader.grade();
			} catch (Exception e) {
				e.printStackTrace();
				try {
					submissionsStorage.setResult(submissionId, null);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} finally {
				lock.unlock();
			}
		};
		new Thread(runnable).start();
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@PostMapping("/user_tests/{user_test_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public synchronized ResponseEntity<?> addUserTest(@PathVariable("user_test_id") String userTestId,
			@RequestParam("instanceId") Optional<String> instanceId,
			@RequestParam("isOfficial") Optional<Boolean> isOfficial,
			@RequestParam("compileTL") Optional<Double> compileTime,
			@RequestParam("compileML") Optional<Integer> compileMemory,
			@RequestPart(name = "metadata") Optional<SubmissionDto> submission,
			@RequestPart("submission") MultipartFile submissionFile,
			@RequestPart("input") MultipartFile[] inputFiles,
			@RequestPart("output") MultipartFile[] outputFiles
			) throws Exception {
		Map<String, List<File>> files = userTestsStorage.storeUserTest(userTestId, submissionFile, Arrays.stream(inputFiles).collect(Collectors.toList()), Arrays.stream(outputFiles).collect(Collectors.toList()));
		scoreUpdated(userTestId, new SubmissionScore("user_tests"));

		Runnable runnable = () -> {
			try {
				lock.lock();
				TaskDetails details = problemsCache.getProblem(Integer.valueOf(submission.get().getProblemId()), instanceId);
				SubmissionGrader grader = new SubmissionGrader(userTestId, isOfficial, details, files.get("submission").get(0).getAbsolutePath(), 
					files.get("inputs").stream().map(f -> f.getAbsolutePath()).collect(Collectors.toList()),
					files.get("outputs").stream().map(f -> f.getAbsolutePath()).collect(Collectors.toList()),
					this, workDir+"/"+piperDir+"/piper", compileTime, compileMemory);
				grader.grade();
			} catch (Exception e) {
				e.printStackTrace();
				try {
					userTestsStorage.setResult(userTestId, null);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} finally {
				lock.unlock();
			}
		};
		new Thread(runnable).start();
		return new ResponseEntity<>(HttpStatus.CREATED);
	}
	
	@Override
	public void scoreUpdated(String submissionId, SubmissionScore score) {
		try {
			if (score.getType().equals("submission")) submissionsStorage.setResult(submissionId, score);
			if (score.getType().equals("user_tests")) userTestsStorage.setResult(submissionId, score);
		} catch (IOException e1) {
			try {
				if (score.getType().equals("submission")) submissionsStorage.setResult(submissionId, score);
				if (score.getType().equals("user_tests")) userTestsStorage.setResult(submissionId, score);
			} catch (IOException e2) {
				if (score.getType().equals("submission")) System.out.println("submission " + submissionId + " failed");
				else if (score.getType().equals("user_tests")) System.out.println("user test " + submissionId + " failed");
				else System.out.println("judging " + submissionId + " failed");
			}
		}
	}

	@GetMapping("/submissions/{submission_id}/score")
	public ResponseEntity<?> getScore(@PathVariable("submission_id") String submissionId) throws Exception {
		SubmissionScore score = submissionsStorage.getResult(submissionId);
		if (score == null) return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
		return ResponseEntity.ok(score);
	}
	
	@GetMapping("/user_tests/{user_test_id}/score")
	public ResponseEntity<?> getScoreUserTest(@PathVariable("user_test_id") String userTestId) throws Exception {
		SubmissionScore score = userTestsStorage.getResult(userTestId);
		if (score == null) return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
		return ResponseEntity.ok(score);
	}

	@GetMapping("/user_tests/{user_test_id}/user_output")
	public ResponseEntity<?> getUserTestUserOutputFile(@PathVariable("user_test_id") String userTestId) throws Exception {
		File userOutputFile = userTestsStorage.getUserOutputFile(userTestId);
		if (!userOutputFile.exists()) return ResponseEntity.ok(null);
		InputStreamResource inputStreamResource = new InputStreamResource(new FileInputStream(userOutputFile));
		org.springframework.http.MediaType mediaType = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=test_user_out")
				.contentLength(userOutputFile.length())
				.contentType(mediaType)
				.body(inputStreamResource);
	}
	}
