package com.group6.cloudsim.workload;

/** Fixed task inputs. Length is in MI per PE and RAM demand is a fraction of VM RAM. */
public record TaskMetadata(long id, WorkloadClass workloadClass, long lengthMiPerPe, int pes, double ramFraction) {}
