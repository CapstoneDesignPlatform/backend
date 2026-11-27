package com.capdi.backend.domain.payment.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class InternalTaxExecutionService {
 private final InternalTaxExecutionPersistenceService persistence;private final FinancialWorkflowService canonical;
 public InternalTaxExecutionPersistenceService.View execute(Long admin,Long id,boolean lookup){
  Long operation=persistence.canonicalOperationId(admin,id);
  if(operation!=null){if(lookup)canonical.reconcile(admin,operation);else canonical.execute(admin,operation);}
  return persistence.read(admin,id);
 }
 /** Legacy scheduler hook; due lookups are claimed only from financial_operations. */
 public int runDue(){return canonical.runDueTaxInvoices();}
}
