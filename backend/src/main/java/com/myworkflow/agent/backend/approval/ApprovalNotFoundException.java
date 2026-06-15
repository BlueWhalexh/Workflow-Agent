package com.myworkflow.agent.backend.approval;

public class ApprovalNotFoundException extends RuntimeException {

  public ApprovalNotFoundException(String approvalId) {
    super("Approval request not found: " + approvalId);
  }
}
