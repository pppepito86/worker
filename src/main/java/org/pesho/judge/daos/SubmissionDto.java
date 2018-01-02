package org.pesho.judge.daos;

public class SubmissionDto {

	private String problemId;
	
	public SubmissionDto() {
	}
	
	public SubmissionDto(String problemId) {
		this.problemId = problemId;
	}
	
	public void setProblemId(String problemId) {
		this.problemId = problemId;
	}
	
	public String getProblemId() {
		return problemId;
	}
	
}
