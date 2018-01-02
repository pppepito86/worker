package org.pesho.judge.rest;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

import org.pesho.grader.SubmissionGrader;
import org.pesho.grader.task.TaskDetails;
import org.pesho.judge.daos.ProblemDto;
import org.pesho.judge.daos.SubmissionDao;
import org.pesho.judge.problems.ProblemsCache;
import org.pesho.judge.problems.SubmissionsStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class RestService {

	@Value("${work.dir}")
	private String workDir;

	@Autowired
	private ProblemsCache problemsCache;

	@Autowired
	private SubmissionsStorage submissionsStorage;

	@GetMapping("/health-check")
	public String healthCheck() {
		return "ok";
	}

	@GetMapping("/problems")
	public Collection<ProblemDto> listProblems() {
		return problemsCache.listProblems();
	}

	@GetMapping("/problems/{problem_id}")
	public ResponseEntity<?> getProblem(@PathVariable("problem_id") int problemId) {
		ProblemDto problem = problemsCache.getProblem(Integer.valueOf(problemId));
		if (problem != null) {
			return new ResponseEntity<>(problem, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
	}

	@PutMapping("/problems/{problem_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> updateProblem(@PathVariable("problem_id") Integer problemId,
			@RequestPart("file") MultipartFile file) throws Exception {
		try {
			problemsCache.updateProblem(problemId, file.getInputStream());
			return new ResponseEntity<>(HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@PostMapping("/problems/{problem_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> addProblem(@PathVariable("problem_id") Integer problemId,
			@RequestPart(name = "metadata", required = false) Optional<ProblemDto> problem,
			@RequestPart("file") MultipartFile file) throws Exception {
		try {
			if (problem.isPresent()) {
				problemsCache.addProblem(problemId, problem.get(), file.getInputStream());
			} else {
				problemsCache.addProblem(problemId, file.getInputStream());
			}
			return new ResponseEntity<>(HttpStatus.CREATED);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/submissions/{submission_id}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> addSubmission(@PathVariable("submission_id") String submissionId,
			@RequestPart(name = "metadata", required = false) Optional<SubmissionDao> submission,
			@RequestPart("file") MultipartFile file)
			throws Exception {
		try {
			File submissionFile = submissionsStorage.storeSubmission(
					submissionId, file.getOriginalFilename(), file.getInputStream());
			TaskDetails taskTests = problemsCache.getProblemNew(Integer.valueOf(submission.get().getProblemId()));
			SubmissionGrader grader = new SubmissionGrader(taskTests, submissionFile.getAbsolutePath());
			grader.grade();
			return new ResponseEntity<>(grader.getScore(), HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}
	}

}
