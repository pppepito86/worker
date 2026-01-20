package org.pesho.judge.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.sql.Timestamp;

import javax.servlet.http.HttpServletRequest;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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

	private Map<String, String> instancesURLs = new ConcurrentHashMap<>();

	private final ReentrantLock lock = new ReentrantLock();

	private List<AbstractMap.Entry<Integer,StepResult>> updates = new ArrayList<>();
	private long lastUpdate = 0L;
	private final long minimumUpdateTime = 500L;

	private ObjectMapper mapper = new ObjectMapper();

	public RestService () {
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}
	
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
			@RequestPart("file") MultipartFile file) {
		try {
			lock.lock();
			System.out.println("Adding problem " + problemId + " from instance " + instanceId);
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
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		} finally {
			lock.unlock();
		}
	}

	@DeleteMapping("/problems/{problem_id}")
	public ResponseEntity<?> deleteProblem(@PathVariable("problem_id") int problemId,
			@RequestParam("instanceId") Optional<String> instanceId) {
		try {
			lock.lock();
			if (problemsCache.getProblem(problemId, instanceId) != null) {
				problemsCache.removeProblem(problemId, instanceId);
				return new ResponseEntity<>(HttpStatus.OK);
			} else {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		} finally {
			lock.unlock();
		}
	}

	@PostMapping("/submissions/{submission_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> addSubmission(@PathVariable("submission_id") String submissionId,
			@RequestParam("instanceId") Optional<String> instanceId,
			@RequestParam("isOfficial") Optional<Boolean> isOfficial,
			@RequestParam("compileTL") Optional<Double> compileTime,
			@RequestParam("compileML") Optional<Integer> compileMemory,
			@RequestParam("points") Optional<Double> points,
			@RequestPart(name = "metadata") Optional<SubmissionDto> submission,
			@RequestPart("file") MultipartFile file,
			@RequestPart("update_time") Optional<Timestamp> updateTime,
			HttpServletRequest request) {
		instanceId.ifPresent(id -> {
			String remoteAddr = request.getRemoteAddr(), port = request.getHeader("X-Client-Port");
			if (("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) && port != null) {
				instancesURLs.put(id, "http://localhost:" + port);
			}
			else {
				String URL = request.getHeader("X-Client-URL");
				if (URL != null) {
					instancesURLs.put(id, URL);
				}
			}
		});

		String id = submissionId + "_" + new Random().nextInt(100);
		File submissionFile;
		try {
			submissionFile = submissionsStorage.storeSubmission(submissionId, id, file.getOriginalFilename(), file.getInputStream(), updateTime.orElse(new Timestamp (0)));
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		scoreUpdatedWithBlocking(id, new SubmissionScore("submission"));

		Runnable runnable = () -> {
			try {
				lock.lock();
				updates.clear();
				lastUpdate = System.currentTimeMillis() - minimumUpdateTime;
				TaskDetails taskTests = problemsCache.getProblem(Integer.valueOf(submission.get().getProblemId()), instanceId);
				SubmissionGrader grader = new SubmissionGrader(id, isOfficial, taskTests, submissionFile.getAbsolutePath(), this, workDir+"/"+piperDir+"/piper", compileTime, compileMemory, points);
				grader.grade();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				submissionsStorage.removeBlock(submissionId);
				lock.unlock();
			}
		};
		new Thread(runnable).start();
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@PostMapping("/user_tests/{user_test_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> addUserTest(@PathVariable("user_test_id") String userTestId,
			@RequestParam("instanceId") Optional<String> instanceId,
			@RequestParam("isOfficial") Optional<Boolean> isOfficial,
			@RequestParam("compileTL") Optional<Double> compileTime,
			@RequestParam("compileML") Optional<Integer> compileMemory,
			@RequestPart("metadata") Optional<SubmissionDto> submission,
			@RequestPart("submission") MultipartFile submissionFile,
			@RequestPart(name="input", required=false) MultipartFile[] inputFiles,
			@RequestPart(name="output", required=false) MultipartFile[] outputFiles,
			@RequestPart("update_time") Optional<Timestamp> updateTime,
			HttpServletRequest request) {
		if (inputFiles == null) inputFiles = new MultipartFile[0];
		if (outputFiles == null) outputFiles = new MultipartFile[0];
		instanceId.ifPresent(id -> {
			String remoteAddr = request.getRemoteAddr(), port = request.getHeader("X-Client-Port");
			if (("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) && port != null) {
				instancesURLs.put(id, "http://localhost:" + port);
			}
			else {
				String URL = request.getHeader("X-Client-URL");
				if (URL != null) {
					instancesURLs.put(id, URL);
				}
			}
		});

		String id = userTestId + "_" + new Random().nextInt(100);
		Map<String, List<File>> files;
		try {
			files = userTestsStorage.storeUserTest(userTestId, id, submissionFile, Arrays.stream(inputFiles).collect(Collectors.toList()), Arrays.stream(outputFiles).collect(Collectors.toList()), updateTime.orElse(new Timestamp(0)));
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		scoreUpdatedWithBlocking(id, new SubmissionScore("user_tests"));

		Runnable runnable = () -> {
			try {
				lock.lock();
				updates.clear();
				lastUpdate = System.currentTimeMillis() - minimumUpdateTime;
				TaskDetails details = problemsCache.getProblem(Integer.valueOf(submission.get().getProblemId()), instanceId);
				SubmissionGrader grader = new SubmissionGrader(id, isOfficial, details, files.get("submission").get(0).getAbsolutePath(), 
					files.get("inputs").stream().map(f -> f.getAbsolutePath()).collect(Collectors.toList()),
					files.get("outputs").stream().map(f -> f.getAbsolutePath()).collect(Collectors.toList()),
					this, workDir+"/"+piperDir+"/piper", compileTime, compileMemory);
				grader.grade();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				userTestsStorage.removeBlock(userTestId);
				lock.unlock();
			}
		};
		new Thread(runnable).start();
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@PostMapping("/submissions/{submission_id}/block")
	public ResponseEntity<?> submissionsBlock(
		@PathVariable("submission_id") String submissionId,
		@RequestPart("update_time") Timestamp updateTime) {
		submissionsStorage.block(submissionId, updateTime);
		System.out.println("Blocked submission " + submissionId + " until update time " + updateTime);
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@PostMapping("/user_tests/{user_test_id}/block")
	public ResponseEntity<?> userTestsBlock(
		@PathVariable("user_test_id") String userTestId,
		@RequestPart("update_time") Timestamp updateTime) {
		userTestsStorage.block(userTestId, updateTime);
		System.out.println("Blocked user test " + userTestId + " until update time " + updateTime);
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	private boolean updateScoreContest (String id, RequestBody requestBody, String api) {
		if (id.split("_").length < 2) return false;
		String instanceId = id.split("_")[1];
		String url = instancesURLs.get(instanceId);
		if (url == null) return false;
		Request request = new Request.Builder()
			.url(url + api)
			.addHeader("X-Instance-Id", instanceId)
			.post(requestBody)
			.build();
		OkHttpClient client = new OkHttpClient().newBuilder()
			.connectTimeout(3, TimeUnit.SECONDS)
			.readTimeout(10, TimeUnit.SECONDS)
			.writeTimeout(10, TimeUnit.SECONDS)
			.build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println("Updating score to " + url + " with API " + api + " failed with code " + response.code());
				return false;
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Updating score to " + url + " with API " + api + " failed");
			return false;
		}
	}

	private void updateStepContest (String id, String type, int number, StepResult result) {
		updates.add(new AbstractMap.SimpleEntry(number, result));
		if (System.currentTimeMillis() - lastUpdate < minimumUpdateTime) return ;
		try {
			RequestBody requestBody;
			requestBody = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("id", id.split("_")[0])
				.addFormDataPart("update_time", null,
						RequestBody.create(mapper.writeValueAsString((type.equals("submission") ? submissionsStorage.getUpdateTime(id) : userTestsStorage.getUpdateTime(id)).toInstant()), okhttp3.MediaType.parse("application/json")))
				.addFormDataPart("steps", null,
						RequestBody.create(mapper.writeValueAsString(updates), okhttp3.MediaType.parse("application/json")))
				.build();
			if (updateScoreContest(id, requestBody, "/api/worker/" + type + "/steps") == true) {
				updates.clear();
				lastUpdate = System.currentTimeMillis();
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Sending updates for " + type + " " + id + " failed");
		}
	}

	@Override
	public boolean setCompileResultWithBlocking(String id, String type, StepResult compileResult) {
		if ((type.equals("submission") && submissionsStorage.checkBlocked(id) == true) ||
			(type.equals("user_tests") && userTestsStorage.checkBlocked(id) == true)) return false;
		updateStepContest(id, type, -1, compileResult);
		return true;
	}

	@Override
	public boolean addTestResultWithBlocking(String id, String type, int testNumber, StepResult testResult) {
		if ((type.equals("submission") && submissionsStorage.checkBlocked(id) == true) ||
			(type.equals("user_tests") && userTestsStorage.checkBlocked(id) == true)) return false;
		updateStepContest(id, type, testNumber, testResult);
		return true;
	}

	@Override
	public boolean scoreUpdatedWithBlocking(String id, SubmissionScore score) {
		String type = score.getType();
		if (!type.equals("submission") && !type.equals("user_tests")) return true;
		boolean blocked = false;
		if (type.equals("submission")) blocked = submissionsStorage.setResult(id, score);
		if (type.equals("user_tests")) blocked = userTestsStorage.setResult(id, score);
			
		if (blocked == false && (score.getCompileResult() == null || score.isFinished() == true)) {
			try {
				RequestBody requestBody;
				requestBody = new MultipartBody.Builder()
					.setType(MultipartBody.FORM)
					.addFormDataPart("id", id.split("_")[0])
					.addFormDataPart("update_time", null,
						RequestBody.create(mapper.writeValueAsString((type.equals("submission") ? submissionsStorage.getUpdateTime(id) : userTestsStorage.getUpdateTime(id)).toInstant()), okhttp3.MediaType.parse("application/json")))
					.addFormDataPart("score", null,
							RequestBody.create(mapper.writeValueAsString(score), okhttp3.MediaType.parse("application/json")))
					.build();
				updateScoreContest(id, requestBody, "/api/worker/" + type + "/score");
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Updating score for " + type + " " + id + " failed");
			}
		}
		return !blocked;
	}

	@GetMapping("/submissions/{submission_id}/score")
	public ResponseEntity<?> getScore(@PathVariable("submission_id") String submissionId) {
		try {
			SubmissionScore score = submissionsStorage.getResult(submissionId);
			if (score == null) return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
			return ResponseEntity.ok(score);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}
	
	@GetMapping("/user_tests/{user_test_id}/score")
	public ResponseEntity<?> getScoreUserTest(@PathVariable("user_test_id") String userTestId) {
		try {
			SubmissionScore score = userTestsStorage.getResult(userTestId);
			if (score == null) return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
			return ResponseEntity.ok(score);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/user_tests/{user_test_id}/user_output")
	public ResponseEntity<?> getUserTestUserOutputFile(@PathVariable("user_test_id") String userTestId) {
		File userOutputFile = userTestsStorage.getUserOutputFile(userTestId);
		if (userOutputFile == null || !userOutputFile.exists()) return ResponseEntity.ok(null);
		try {
			InputStreamResource inputStreamResource = new InputStreamResource(new FileInputStream(userOutputFile));
			org.springframework.http.MediaType mediaType = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=test_user_out")
					.contentLength(userOutputFile.length())
					.contentType(mediaType)
					.body(inputStreamResource);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}

}
