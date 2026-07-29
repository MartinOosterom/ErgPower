## ADDED Requirements

### Requirement: Metric registry and display modes
The app SHALL describe every displayable measurement in a single registry entry declaring its label,
unit, how to read its current value, and which **display modes** are meaningful: a **value** tile and/or
a **value-vs-time graph**. The value and graph widgets SHALL both be driven by this registry, with no
per-metric hardcoding. For every metric marked graphable, the shared live store SHALL keep a bounded
rolling history so a graph can be added for it.

#### Scenario: Any graphable metric can be graphed
- **WHEN** a measurement is marked graphable in the registry
- **THEN** a value-vs-time graph can be added for it, and it updates at the stream cadence

#### Scenario: Non-graphable metric offers only a value
- **WHEN** a measurement is not graphable (e.g. elapsed time)
- **THEN** only a value tile is offered for it, not a graph

#### Scenario: One registry, no per-metric components
- **WHEN** a new measurement is added to the registry with its display modes
- **THEN** it becomes available as a value tile (and a graph if graphable) with no new widget component

## MODIFIED Requirements

### Requirement: Core widgets
The app SHALL provide these core widgets: a **value tile** and a **value-vs-time graph** — both
parameterized over the single metric registry, so any registered measurement can be shown as a value,
and any graphable measurement can also be shown as a graph — plus **ForceCurve**, **GoalProgress**,
**WorkoutPhase**, and **ConnectionStatus**. The set of values offered as tiles and the set offered as
graphs SHALL come from the registry (not a hardcoded per-metric list).

#### Scenario: A measurement as a value or a graph
- **WHEN** the user adds a panel for a graphable measurement (e.g. power)
- **THEN** they may choose a value tile or a value-vs-time graph, and it updates at the stream cadence

#### Scenario: Live tiles and graphs update
- **WHEN** metrics events arrive
- **THEN** the corresponding value tiles and graphs update at the stream cadence

### Requirement: Availability-aware widget palette
Each widget/panel SHALL declare the data it requires; the palette SHALL show those whose required data
the current API does not provide as **disabled**. For metric panels the palette SHALL act as a
metric → display-mode picker: it offers each measurement's available display modes (value, and graph
only when the registry marks the metric graphable) rather than a flat, ever-growing list, and SHALL NOT
offer a display mode a metric does not support.

#### Scenario: Unavailable data is disabled
- **WHEN** a registered widget/panel requires data not present in the live API
- **THEN** the palette shows it disabled (not addable) rather than broken

#### Scenario: Graph mode only for graphable metrics
- **WHEN** the user browses display modes for a measurement in the palette
- **THEN** a graph mode is offered only if the registry marks that measurement graphable
