# Fair Resource Allocation for Mixed Batch, Interactive and AI-Inference Workloads in Cloud Computing

IIIT Sri City · Cloud Computing · Group 6 · Topic 22

## Research problem

A shared cloud data center serves workloads with different resource needs and timing constraints. Batch jobs need steady progress, interactive requests need low latency, and inference requests need predictable response times while consuming CPU and RAM.

The project will evaluate Workload-Aware Weighted Multi-Resource Fairness with Latency Guard (WMRF-LG), a project-specific scheduling policy. All policy comparisons will use identical workload inputs and infrastructure configurations. Planned measurements include mean and P95/P99 response times, throughput, CPU and RAM utilization, batch completion time, SLA miss ratio, and Jain's fairness index.

## Milestone 2: reproducible workloads

The default application now generates and runs a 120-task balanced research workload using seed 42,
a rate of 0.5 tasks/second and a 600-second arrival horizon. The research topology has three
four-PE hosts and six two-PE VMs, each at 1,000 MIPS per PE. Hosts have 16,384 MB RAM; VMs have
4,096 MB. Configuration validates per-host CPU, RAM, bandwidth and storage fit without overcommit.

| Scenario | Batch | Interactive | AI inference |
| --- | ---: | ---: | ---: |
| BALANCED | 48 (40%) | 42 (35%) | 30 (25%) |
| BATCH_HEAVY | 72 (60%) | 24 (20%) | 24 (20%) |
| INTERACTIVE_HEAVY | 24 (20%) | 72 (60%) | 24 (20%) |
| INFERENCE_HEAVY | 24 (20%) | 24 (20%) | 72 (60%) |

Immutable task records retain unique IDs, classes, arrivals, length in MI per PE, PE/RAM demands,
optional response targets and seed. Seeded exponential inter-arrival times follow the configured
rate. Deterministic largest-remainder allocation preserves the exact task count, and seeded
shuffling avoids fixed class priority. A horizon that cannot fit the sampled count causes a clear
error; tasks are never dropped, clipped or rescaled. Repeating the same configuration reproduces
identical records, and each run builds fresh Cloudlets from that trace.

Cloudlet submission delays use a measured workload epoch when all VMs are created. Absolute
datacenter arrival equals that epoch plus trace arrival; tests verify actual arrivals and starts.
RAM demand is stored in MB and mapped to an `ABSOLUTE` Cloudlet RAM utilization model. This models
resource demand but does **not** enforce concurrent task RAM reservations. The later admission
ledger has an interface only. Response targets are experimental assumptions for future policies.

See [WORKLOAD_MODEL.md](docs/WORKLOAD_MODEL.md) for configurable ranges, timing/RAM semantics,
Java examples, reproducibility and limitations; see [executed verification](docs/MILESTONE_2_VERIFICATION.md)
for actual output and pull-request preparation instructions.

```powershell
mvn clean test
mvn exec:java
mvn exec:java '-Dexec.args=BATCH_HEAVY 120 42 600 0.5'
mvn exec:java '-Dexec.args=INTERACTIVE_HEAVY 120 42 600 0.5'
mvn exec:java '-Dexec.args=INFERENCE_HEAVY 120 42 600 0.5'
mvn exec:java '-Dexec.args=BALANCED 120 42 600 0.5'
mvn exec:java '-Dexec.args=--smoke'
```

Arguments are scenario, task count, seed, horizon seconds and rate tasks/second, with defaults for
omitted trailing arguments. Custom profiles and infrastructure capacities are Java configuration
inputs. No scheduling baseline, WMRF-LG, final metrics or experiment matrix is implemented here.

## Preserved Milestone 1 smoke scenario

A deterministic CloudSim Plus scenario creates one datacenter, one physical host, one VM, and three Cloudlets. A broker places the VM, submits all three tasks, and collects their completion records. The host and VM use CloudSim Plus space-shared scheduling. Each Cloudlet requests full CPU use on its assigned processing elements (PEs), and the four VM PEs allow all three tasks to execute concurrently.

| Resource | Physical host | VM |
| --- | ---: | ---: |
| CPU processing elements | 8 | 4 |
| MIPS per PE | 2,000 | 2,000 |
| RAM (MB) | 16,384 | 4,096 |
| Bandwidth (Mbps) | 10,000 | 1,000 |
| Storage (MB) | 1,000,000 | 20,000 |

| Workload | Scenario profile | Length (MI per PE) | PEs | RAM demand (MB; original approximate share) |
| --- | --- | ---: | ---: | ---: |
| Batch | Long-running analytics job | 40,000 | 1 | 614 (15%) |
| Interactive | Short, latency-sensitive request | 2,000 | 1 | 410 (10%) |
| AI inference | Abstract CPU- and RAM-intensive prediction request | 12,000 | 2 | 2,253 (55%) |

The scenario models inference with CPU work and RAM demand. It has no neural network execution or GPU model. Its output reports elapsed simulation times and task status. Policy comparisons, overload and SLA experiments, and research metrics are planned for later milestones.

## Technology and prerequisites

- Java and Apache Maven on `PATH`. The original environment used JDK 25.0.1/Maven 3.10.1; Milestone 2 was actually verified on Windows 11 with installed JDK 24.0.2 and Maven 3.9.9. Maven compiles project code with Java release 17.
- CloudSim Plus `org.cloudsimplus:cloudsimplus:8.5.7` for discrete-event simulation.
- JUnit Jupiter 5.11.4 for unit and integration tests.
- Access to Maven Central for the initial dependency and plugin downloads.

## Build, test, and run on Windows

Run these PowerShell commands from the repository root:

```powershell
mvn -version
mvn clean test
mvn exec:java
mvn dependency:tree
```

`mvn clean test` compiles the application and runs the tests. Use `mvn clean package` to also build the project JAR. On a fresh checkout, compile before running the Exec plugin with `mvn compile exec:java`.

The application prints a table of broker-returned Cloudlets with workload class, status, VM, PEs, start time, finish time, and execution duration. The CloudSim Plus startup banner displays `8.5.1` because the 8.5.7 artifact still uses that [version constant](https://javadoc.io/static/org.cloudsimplus/cloudsimplus/8.5.7/constant-values.html#org.cloudsimplus.core.CloudSimPlus.VERSION). The resolved Maven artifact version appears in `mvn dependency:tree`.

## Architecture and layout

```text
pom.xml
README.md
docs/
  CONTRIBUTION_PLAN.md
  WORKLOAD_MODEL.md
  MILESTONE_2_VERIFICATION.md
src/
  main/java/com/group6/cloudsim/
    Main.java
    core/             SimulationConfig, SimulationController
    datacenter/       DatacenterFactory
    workload/         CloudletFactory, TaskMetadata, WorkloadClass
                      WorkloadProfile, WorkloadScenario, WorkloadGenerationConfig
                      WorkloadGenerator, WorkloadTrace, WorkloadValidator, RamAdmissionLedger
  test/java/com/group6/cloudsim/
    core/             SimulationControllerTest, SimulationConfigTest, WorkloadSimulationTest
    datacenter/       DatacenterFactoryTest
    workload/         WorkloadGeneratorTest, WorkloadValidatorTest, RamModelTest
```

`Main` selects the research scenario or smoke test and displays results. `WorkloadGenerationConfig`,
`WorkloadProfile` and `WorkloadScenario` describe inputs; `WorkloadGenerator` produces a validated
immutable `WorkloadTrace` through `WorkloadValidator`. These generation modules do not depend on
CloudSim. `SimulationConfig` defines validated infrastructure capacities and counts.
`DatacenterFactory` creates hosts/VMs, and `CloudletFactory` creates fresh Cloudlets from task records.
`SimulationController` owns a fresh simulator/broker, records the VM-ready workload epoch and
returns completed Cloudlets with original metadata. `RamAdmissionLedger` defines a later integration
contract without implementing reservation. Unit/integration tests inspect real resources, arrivals,
RAM accounting, repeatability and broker results.

## Team

| Member | Responsibility |
| --- | --- |
| Akshith Veerisetty | Architecture, scenario configuration, orchestration, proposed scheduling policy, and integration |
| Sajja Venkata Naveen | Datacenter and VM infrastructure, workload models and generation, and VM placement |
| Pitti Aswarth Narayana | Baseline policies, metrics and fairness calculations, experiment execution, and reporting |

Module assignments and the private repository workflow are in the [contribution plan](docs/CONTRIBUTION_PLAN.md).

## References

- [CloudSim Plus 8.5.7 API reference](https://javadoc.io/static/org.cloudsimplus/cloudsimplus/8.5.7/index.html)
- [Official CloudSim Plus examples](https://github.com/cloudsimplus/cloudsimplus-examples)

## License

[MIT](LICENSE)
