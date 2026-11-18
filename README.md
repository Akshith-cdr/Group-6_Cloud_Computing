# Fair Resource Allocation for Mixed Batch, Interactive and AI-Inference Workloads in Cloud Computing

IIIT Sri City · Cloud Computing · Group 6 · Topic 22

## Research problem

A shared cloud data center serves workloads with different resource needs and timing constraints. Batch jobs need steady progress, interactive requests need low latency, and inference requests need predictable response times while consuming CPU and RAM.

The project will evaluate Workload-Aware Weighted Multi-Resource Fairness with Latency Guard (WMRF-LG), a project-specific scheduling policy. All policy comparisons will use identical workload inputs and infrastructure configurations. Planned measurements include mean and P95/P99 response times, throughput, CPU and RAM utilization, batch completion time, SLA miss ratio, and Jain's fairness index.

## Milestone 1

A deterministic CloudSim Plus scenario creates one datacenter, one physical host, one VM, and three Cloudlets. A broker places the VM, submits all three tasks, and collects their completion records. The host and VM use CloudSim Plus space-shared scheduling. Each Cloudlet requests full CPU use on its assigned processing elements (PEs), and the four VM PEs allow all three tasks to execute concurrently.

| Resource | Physical host | VM |
| --- | ---: | ---: |
| CPU processing elements | 8 | 4 |
| MIPS per PE | 2,000 | 2,000 |
| RAM (MB) | 16,384 | 4,096 |
| Bandwidth (Mbps) | 10,000 | 1,000 |
| Storage (MB) | 1,000,000 | 20,000 |

| Workload | Scenario profile | Length (MI per PE) | PEs | Modeled share of VM RAM |
| --- | --- | ---: | ---: | ---: |
| Batch | Long-running analytics job | 40,000 | 1 | 15% |
| Interactive | Short, latency-sensitive request | 2,000 | 1 | 10% |
| AI inference | Abstract CPU- and RAM-intensive prediction request | 12,000 | 2 | 55% |

The scenario models inference with CPU work and RAM demand. It has no neural network execution or GPU model. Its output reports elapsed simulation times and task status. Policy comparisons, overload and SLA experiments, and research metrics are planned for later milestones.

## Technology and prerequisites

- JDK 25 and system Apache Maven on `PATH`. The verification environment uses Windows 11, JDK 25.0.1, and Maven 3.10.1. Maven compiles project code with Java release 17.
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
src/
  main/java/com/group6/cloudsim/
    Main.java
    core/             SimulationConfig, SimulationController
    datacenter/       DatacenterFactory
    workload/         CloudletFactory, TaskMetadata, WorkloadClass
  test/java/com/group6/cloudsim/
    core/             SimulationControllerTest
    datacenter/       DatacenterFactoryTest
```

`Main` runs the scenario and displays results. `SimulationConfig` defines the infrastructure and workload inputs. `DatacenterFactory` creates the host, datacenter, and VM, and `CloudletFactory` creates the Cloudlets. `SimulationController` creates a fresh simulator and broker for each run, submits the VM and tasks, and returns the completed Cloudlets with their original workload metadata. The tests inspect resources and broker results directly.

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
