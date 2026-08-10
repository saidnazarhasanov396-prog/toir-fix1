-- Seed safety checklist templates/items, maintenance action safety notes,
-- and backfill work-order safety checklists for AI work-order consumption.
-- Idempotent: safe if tables already partially seeded.

-- Safety rules seed for AI work-order consumption.
-- Idempotent. Safe to re-run on demo or empty prod/stage.
-- AI path: work_orders -> work_order_safety_checklists/items
--          work_orders -> work_order_tasks (Safety:/Tools:)
--          maintenance_operations.safety_notes + maintenance_actions.safety_notes

-- ===========================================================================
-- 1) Maintenance action catalog (reusable safety rules)
-- ===========================================================================
INSERT INTO maintenance_actions (
    id, created_at, updated_at, is_deleted, code, name, category,
    default_duration_hours, required_skill, safety_notes,
    tools_required, spare_parts_required, consumables_required, is_active
)
SELECT
    seed.id, now(), now(), false, seed.code, seed.name, seed.category,
    seed.hours, seed.skill, seed.safety, seed.tools, seed.spares, seed.consumables, true
FROM (VALUES
    ('10000000-0000-0000-0000-000000410001'::uuid, 'ACT-OIL-CHANGE', 'Engine / gearbox oil change', 'LUBRICATION', 2.0, 'MECHANIC',
     'Wear oil-resistant gloves and safety glasses. Stop motor and apply LOTO before draining. Place drip trays; never open hot sump. Confirm no rotating parts before removing drain plug.',
     'Drain pan, wrenches, torque wrench, funnel', 'oil filter, sealing ring', 'engine oil, rags, absorbent'),
    ('10000000-0000-0000-0000-000000410002'::uuid, 'ACT-LOTO-ISOLATION', 'LOTO and energy isolation', 'SAFETY', 1.0, 'FOREMAN',
     'Apply lockout/tagout on all energy sources (electrical, pneumatic, hydraulic). Verify zero energy with meter/gauge. Only the person who applied LOTO may remove it.',
     'LOTO kit, multimeter, pressure gauge', NULL::text, 'tags, padlocks'),
    ('10000000-0000-0000-0000-000000410003'::uuid, 'ACT-MOTOR-REMOTE-START', 'Remote motor start zone clearance', 'SAFETY', 0.5, 'FOREMAN',
     'If remote start is possible, clear the remote-start zone, post warning signs, and block start at local panel before mechanical work begins.',
     'Warning signs, radio', NULL::text, NULL::text),
    ('10000000-0000-0000-0000-000000410004'::uuid, 'ACT-SEAL-REPLACEMENT', 'Mechanical seal replacement', 'REPAIR', 3.0, 'MECHANIC',
     'Depressurize and drain the line. Use chemical-resistant gloves for process fluid. Do not reuse damaged seal faces; inspect shaft sleeve before assembly.',
     'Puller, dial indicator, torque wrench', 'mechanical seal, gaskets', 'cleaner, lubricant'),
    ('10000000-0000-0000-0000-000000410005'::uuid, 'ACT-VIBRATION-CHECK', 'Vibration and bearing inspection', 'INSPECTION', 2.0, 'INSPECTOR',
     'Keep hands/tools away from rotating equipment during test run. Use hearing protection. Stop unit before touching couplings or guards.',
     'Vibration meter, stethoscope, IR thermometer', NULL::text, NULL::text),
    ('10000000-0000-0000-0000-000000410006'::uuid, 'ACT-BEARING-REPLACE', 'Bearing replacement', 'REPAIR', 4.0, 'MECHANIC',
     'Isolate drive and confirm shaft is locked. Use induction heater / press correctly; never hammer hot bearings. Wear heat-resistant gloves. Recheck clearance and lubrication before restart.',
     'Bearing heater, press, feeler gauges', 'bearing, retainer', 'grease'),
    ('10000000-0000-0000-0000-000000410007'::uuid, 'ACT-ELECTRICAL-DIAG', 'Electrical insulation diagnostics', 'DIAGNOSTICS', 2.0, 'ELECTRICIAN',
     'De-energize and prove dead before insulation test. Use CAT-rated gloves and insulated tools. Discharge capacitors. Do not megger circuits with sensitive electronics connected.',
     'Megger, multimeter, insulated tools', NULL::text, NULL::text),
    ('10000000-0000-0000-0000-000000410008'::uuid, 'ACT-HX-CLEAN', 'Heat exchanger cleaning', 'REPAIR', 6.0, 'MECHANIC',
     'Isolate and depressurize both sides. Drain and vent. Wear chemical PPE for cleaning agents. Confined-space permit if entering channel/header. Flush thoroughly before return to service.',
     'Torque wrench, cleaning lances', 'gaskets', 'cleaner, neutralizing agent'),
    ('10000000-0000-0000-0000-000000410009'::uuid, 'ACT-VALVE-CAL', 'Control valve calibration', 'DIAGNOSTICS', 2.0, 'INSTRUMENT',
     'Isolate process and vent trapped pressure. Stroke valve only after confirming no personnel in stroke path. Lock out air/electrical signal as needed.',
     'HART communicator, pressure calibrator', NULL::text, NULL::text),
    ('10000000-0000-0000-0000-000000410010'::uuid, 'ACT-HOT-WORK', 'Hot work (weld/grind)', 'SAFETY', 1.0, 'WELDER',
     'Valid hot-work permit required. Clear combustibles, set fire watch, and keep extinguisher ready. Stop work if gas detection alarms. Cool and inspect area after completion.',
     'Welding set, screens, extinguisher', NULL::text, 'welding consumables')
) AS seed(id, code, name, category, hours, skill, safety, tools, spares, consumables)
WHERE NOT EXISTS (
    SELECT 1 FROM maintenance_actions e
    WHERE upper(e.code) = upper(seed.code) AND e.is_deleted = false
);

UPDATE maintenance_actions ma
SET updated_at = now(),
    name = seed.name,
    category = seed.category,
    default_duration_hours = seed.hours,
    required_skill = seed.skill,
    safety_notes = seed.safety,
    tools_required = seed.tools,
    spare_parts_required = seed.spares,
    consumables_required = seed.consumables,
    is_active = true
FROM (VALUES
    ('ACT-OIL-CHANGE', 'Engine / gearbox oil change', 'LUBRICATION', 2.0, 'MECHANIC',
     'Wear oil-resistant gloves and safety glasses. Stop motor and apply LOTO before draining. Place drip trays; never open hot sump. Confirm no rotating parts before removing drain plug.',
     'Drain pan, wrenches, torque wrench, funnel', 'oil filter, sealing ring', 'engine oil, rags, absorbent'),
    ('ACT-LOTO-ISOLATION', 'LOTO and energy isolation', 'SAFETY', 1.0, 'FOREMAN',
     'Apply lockout/tagout on all energy sources (electrical, pneumatic, hydraulic). Verify zero energy with meter/gauge. Only the person who applied LOTO may remove it.',
     'LOTO kit, multimeter, pressure gauge', NULL::text, 'tags, padlocks'),
    ('ACT-MOTOR-REMOTE-START', 'Remote motor start zone clearance', 'SAFETY', 0.5, 'FOREMAN',
     'If remote start is possible, clear the remote-start zone, post warning signs, and block start at local panel before mechanical work begins.',
     'Warning signs, radio', NULL::text, NULL::text),
    ('ACT-SEAL-REPLACEMENT', 'Mechanical seal replacement', 'REPAIR', 3.0, 'MECHANIC',
     'Depressurize and drain the line. Use chemical-resistant gloves for process fluid. Do not reuse damaged seal faces; inspect shaft sleeve before assembly.',
     'Puller, dial indicator, torque wrench', 'mechanical seal, gaskets', 'cleaner, lubricant'),
    ('ACT-VIBRATION-CHECK', 'Vibration and bearing inspection', 'INSPECTION', 2.0, 'INSPECTOR',
     'Keep hands/tools away from rotating equipment during test run. Use hearing protection. Stop unit before touching couplings or guards.',
     'Vibration meter, stethoscope, IR thermometer', NULL::text, NULL::text),
    ('ACT-BEARING-REPLACE', 'Bearing replacement', 'REPAIR', 4.0, 'MECHANIC',
     'Isolate drive and confirm shaft is locked. Use induction heater / press correctly; never hammer hot bearings. Wear heat-resistant gloves. Recheck clearance and lubrication before restart.',
     'Bearing heater, press, feeler gauges', 'bearing, retainer', 'grease'),
    ('ACT-ELECTRICAL-DIAG', 'Electrical insulation diagnostics', 'DIAGNOSTICS', 2.0, 'ELECTRICIAN',
     'De-energize and prove dead before insulation test. Use CAT-rated gloves and insulated tools. Discharge capacitors. Do not megger circuits with sensitive electronics connected.',
     'Megger, multimeter, insulated tools', NULL::text, NULL::text),
    ('ACT-HX-CLEAN', 'Heat exchanger cleaning', 'REPAIR', 6.0, 'MECHANIC',
     'Isolate and depressurize both sides. Drain and vent. Wear chemical PPE for cleaning agents. Confined-space permit if entering channel/header. Flush thoroughly before return to service.',
     'Torque wrench, cleaning lances', 'gaskets', 'cleaner, neutralizing agent'),
    ('ACT-VALVE-CAL', 'Control valve calibration', 'DIAGNOSTICS', 2.0, 'INSTRUMENT',
     'Isolate process and vent trapped pressure. Stroke valve only after confirming no personnel in stroke path. Lock out air/electrical signal as needed.',
     'HART communicator, pressure calibrator', NULL::text, NULL::text),
    ('ACT-HOT-WORK', 'Hot work (weld/grind)', 'SAFETY', 1.0, 'WELDER',
     'Valid hot-work permit required. Clear combustibles, set fire watch, and keep extinguisher ready. Stop work if gas detection alarms. Cool and inspect area after completion.',
     'Welding set, screens, extinguisher', NULL::text, 'welding consumables')
) AS seed(code, name, category, hours, skill, safety, tools, spares, consumables)
WHERE upper(ma.code) = upper(seed.code) AND ma.is_deleted = false;

UPDATE maintenance_operations mo
SET safety_notes = CASE mo.name
    WHEN 'Isolation and LOTO' THEN
        'Apply LOTO on electrical and mechanical energy sources. Verify zero energy. Post Do Not Start tags before disassembly.'
    WHEN 'Disassembly' THEN
        'Use PPE (gloves, safety glasses). Secure heavy parts with lifting aids. Never place hands under suspended loads.'
    WHEN 'Inspection' THEN
        'Inspect only on stopped equipment unless remote monitoring is used. Keep clear of coupling and belt paths.'
    WHEN 'Replacement/adjustment' THEN
        'Confirm correct spare and torque values. Wear gloves when handling oils/seals. Clean mating surfaces before assembly.'
    WHEN 'Assembly and test' THEN
        'Remove tools from work area before startup. Clear remote start zone. Verify guards installed before test run.'
    ELSE mo.safety_notes
END,
updated_at = now()
WHERE mo.is_deleted = false
  AND (mo.safety_notes IS NULL OR btrim(mo.safety_notes) = '' OR mo.safety_notes = 'Follow safety permit and PPE instructions');

UPDATE maintenance_operations mo SET action_id = '10000000-0000-0000-0000-000000410002', updated_at = now()
WHERE mo.is_deleted = false AND mo.name = 'Isolation and LOTO'
  AND (mo.action_id IS NULL OR mo.action_id <> '10000000-0000-0000-0000-000000410002');

UPDATE maintenance_operations mo SET action_id = '10000000-0000-0000-0000-000000410005', updated_at = now()
WHERE mo.is_deleted = false AND mo.name = 'Inspection' AND mo.action_id IS NULL;

UPDATE maintenance_operations mo SET action_id = '10000000-0000-0000-0000-000000410004', updated_at = now()
WHERE mo.is_deleted = false AND mo.name = 'Replacement/adjustment' AND mo.action_id IS NULL;

-- ===========================================================================
-- 2) Safety checklist templates (by WO type / work type)
-- ===========================================================================
INSERT INTO safety_checklist_templates (
    id, created_at, updated_at, is_deleted, code, name, work_order_type, work_type, active, description
) VALUES
('10000000-0000-0000-0000-000000400001', now(), now(), false, 'SAF-TPL-PLANNED', 'Planned maintenance safety checklist', 'PLANNED', NULL, true, 'PPE, LOTO, depressurize and permit checks for planned PPR/PM work.'),
('10000000-0000-0000-0000-000000400002', now(), now(), false, 'SAF-TPL-DEFECT', 'Defect repair safety checklist', 'DEFECT', NULL, true, 'Safety checklist for defect-driven repair work orders.'),
('10000000-0000-0000-0000-000000400003', now(), now(), false, 'SAF-TPL-EMERGENCY', 'Emergency work safety checklist', 'EMERGENCY', NULL, true, 'Emergency briefing, isolation and hot-work controls.'),
('10000000-0000-0000-0000-000000400005', now(), now(), false, 'SAF-TPL-INSPECTION', 'Inspection work safety checklist', 'INSPECTION', NULL, true, 'Rules for inspection and condition monitoring work.'),
('10000000-0000-0000-0000-000000400006', now(), now(), false, 'SAF-TPL-OVERHAUL', 'Overhaul safety checklist', 'OVERHAUL', NULL, true, 'Extended overhaul: LOTO, lifting, confined space, restart.'),
('10000000-0000-0000-0000-000000400007', now(), now(), false, 'SAF-TPL-MEDIUM-REPAIR', 'Medium repair safety checklist', 'MEDIUM_REPAIR', NULL, true, 'Medium repair isolation and PPE checklist.'),
('10000000-0000-0000-0000-000000400008', now(), now(), false, 'SAF-TPL-CAPITAL-REPAIR', 'Capital repair safety checklist', 'CAPITAL_REPAIR', NULL, true, 'Capital repair multi-craft safety checklist.'),
('10000000-0000-0000-0000-000000400009', now(), now(), false, 'SAF-TPL-REPLACEMENT', 'Replacement work safety checklist', NULL, 'REPLACEMENT', true, 'Used when WO type has no template but work_type=REPLACEMENT.'),
('10000000-0000-0000-0000-000000400010', now(), now(), false, 'SAF-TPL-DIAGNOSTICS', 'Diagnostics work safety checklist', NULL, 'DIAGNOSTICS', true, 'Used when WO type has no template but work_type=DIAGNOSTICS.'),
('10000000-0000-0000-0000-000000400004', now(), now(), false, 'SAF-TPL-DEFAULT', 'Default maintenance safety checklist', NULL, NULL, true, 'Fallback checklist when no type/work-type template matches.')
ON CONFLICT (code) DO UPDATE SET
    updated_at = EXCLUDED.updated_at,
    name = EXCLUDED.name,
    work_order_type = EXCLUDED.work_order_type,
    work_type = EXCLUDED.work_type,
    active = EXCLUDED.active,
    description = EXCLUDED.description;

INSERT INTO safety_checklist_template_items (
    id, created_at, updated_at, is_deleted, template_id, sequence, label, description,
    category, critical, requires_comment, active
)
SELECT * FROM (VALUES
    ('10000000-0000-0000-0000-000000420101'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400001'::uuid, 1,
     'Wear PPE (gloves and safety glasses)', 'Oil/chemical resistant gloves and eye protection before opening panels or handling lubricants.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420102'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400001'::uuid, 2,
     'Stop and lock out motor', 'Equipment stopped and LOTO applied before oil change or mechanical access.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420103'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400001'::uuid, 3,
     'Remote start zone cleared', 'Block remote start and post warning signs if remote start is possible.', 'WORKPLACE', true, true, true),
    ('10000000-0000-0000-0000-000000420104'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400001'::uuid, 4,
     'Depressurize before opening', 'Release and verify zero pressure before opening lines/housings/seal chambers.', 'PRESSURE_RELEASE', true, false, true),
    ('10000000-0000-0000-0000-000000420105'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400001'::uuid, 5,
     'Work permit confirmed', 'Valid work/safety permit issued and visible at work location.', 'PERMIT', true, false, true),
    ('10000000-0000-0000-0000-000000420106'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400001'::uuid, 6,
     'Correct tools selected', 'Inspect tools; use only fit-for-purpose and calibrated instruments.', 'TOOL', false, false, true),
    ('10000000-0000-0000-0000-000000420107'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400001'::uuid, 7,
     'Guards restored before restart', 'All guards and covers reinstalled before test run.', 'GUARDING', true, false, true),
    ('10000000-0000-0000-0000-000000420201'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400002'::uuid, 1,
     'Wear PPE (gloves and safety glasses)', 'Minimum PPE before repair on leaking or hot equipment.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420202'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400002'::uuid, 2,
     'LOTO applied and verified', 'All energy sources isolated; zero-energy verified before disassembly.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420203'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400002'::uuid, 3,
     'Spill containment ready', 'Drip trays/absorbents ready for seal or oil work.', 'WORKPLACE', false, false, true),
    ('10000000-0000-0000-0000-000000420204'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400002'::uuid, 4,
     'Pressure released', 'Process and seal chamber depressurized before opening.', 'PRESSURE_RELEASE', true, false, true),
    ('10000000-0000-0000-0000-000000420205'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400002'::uuid, 5,
     'Guards and covers restored', 'Guards reinstalled before returning equipment to service.', 'GUARDING', true, false, true),
    ('10000000-0000-0000-0000-000000420206'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400002'::uuid, 6,
     'Defect hazard noted', 'Known defect hazard (leak, heat, vibration) briefed to the crew.', 'OTHER', false, true, true),
    ('10000000-0000-0000-0000-000000420301'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400003'::uuid, 1,
     'Emergency briefing completed', 'Team briefed on immediate hazards and escape routes.', 'WORKPLACE', true, false, true),
    ('10000000-0000-0000-0000-000000420302'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400003'::uuid, 2,
     'LOTO applied where possible', 'Isolate energy if safe before emergency repair.', 'ENERGY_ISOLATION', true, true, true),
    ('10000000-0000-0000-0000-000000420303'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400003'::uuid, 3,
     'Fire watch / hot work controls', 'Hot work and fire watch rules applied when welding/grinding required.', 'PERMIT', true, false, true),
    ('10000000-0000-0000-0000-000000420304'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400003'::uuid, 4,
     'Emergency PPE donned', 'Face shield/chemical suit as required by the emergency scenario.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420305'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400003'::uuid, 5,
     'Area cordoned', 'Unauthorized access blocked around emergency work zone.', 'WORKPLACE', true, false, true),
    ('10000000-0000-0000-0000-000000420501'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400005'::uuid, 1,
     'Hearing and eye protection', 'Required near running machinery during inspection.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420502'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400005'::uuid, 2,
     'Keep clear of rotating parts', 'No contact with couplings, belts, shafts while running.', 'GUARDING', true, false, true),
    ('10000000-0000-0000-0000-000000420503'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400005'::uuid, 3,
     'Stop before intrusive check', 'Stop and isolate before opening covers or touching bearings.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420504'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400005'::uuid, 4,
     'Calibrated instruments used', 'Vibration/temperature instruments within calibration date.', 'TOOL', false, false, true),
    ('10000000-0000-0000-0000-000000420601'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400006'::uuid, 1,
     'Full energy isolation', 'Electrical, mechanical, hydraulic and pneumatic LOTO complete.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420602'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400006'::uuid, 2,
     'Lifting plan approved', 'Certified lifting gear and tagged loads for heavy parts.', 'WORKPLACE', true, true, true),
    ('10000000-0000-0000-0000-000000420603'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400006'::uuid, 3,
     'Confined space controls', 'Permit and gas test if entering housings/vessels.', 'PERMIT', true, true, true),
    ('10000000-0000-0000-0000-000000420604'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400006'::uuid, 4,
     'PPE for overhaul', 'Hard hat, gloves, glasses, safety shoes mandatory.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420605'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400006'::uuid, 5,
     'Pre-startup checklist', 'Guards, fasteners, oil levels and tools-out confirmed before restart.', 'GUARDING', true, false, true),
    ('10000000-0000-0000-0000-000000420701'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400007'::uuid, 1,
     'PPE confirmed', 'Task PPE worn before starting medium repair.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420702'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400007'::uuid, 2,
     'Isolation verified', 'LOTO applied and verified before opening equipment.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420703'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400007'::uuid, 3,
     'Pressure released', 'Lines depressurized before flange/seal work.', 'PRESSURE_RELEASE', true, false, true),
    ('10000000-0000-0000-0000-000000420704'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400007'::uuid, 4,
     'Tools fit for purpose', 'Correct and inspected tools ready.', 'TOOL', false, false, true),
    ('10000000-0000-0000-0000-000000420801'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400008'::uuid, 1,
     'Multi-craft briefing', 'Mechanical/electrical/instrument hazards reviewed.', 'WORKPLACE', true, false, true),
    ('10000000-0000-0000-0000-000000420802'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400008'::uuid, 2,
     'Complete LOTO', 'All energy sources locked and tagged by craft.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420803'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400008'::uuid, 3,
     'Hot work permit if needed', 'Welding/grinding only under valid permit.', 'PERMIT', true, true, true),
    ('10000000-0000-0000-0000-000000420804'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400008'::uuid, 4,
     'Scaffolding / access safe', 'Access platforms inspected and tagged.', 'WORKPLACE', true, false, true),
    ('10000000-0000-0000-0000-000000420805'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400008'::uuid, 5,
     'Final guarding check', 'All guards and covers fitted before energization.', 'GUARDING', true, false, true),
    ('10000000-0000-0000-0000-000000420901'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400009'::uuid, 1,
     'Correct spare verified', 'Part number and condition checked before install.', 'OTHER', true, false, true),
    ('10000000-0000-0000-0000-000000420902'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400009'::uuid, 2,
     'Isolation before swap', 'LOTO and depressurize before removing old part.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420903'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400009'::uuid, 3,
     'PPE for handling', 'Gloves/glasses for oils, sharp edges, chemicals.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420904'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400009'::uuid, 4,
     'Torque and alignment', 'Apply specified torque; check alignment after install.', 'TOOL', false, false, true),
    ('10000000-0000-0000-0000-000000421001'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400010'::uuid, 1,
     'Live work controls', 'If diagnostics under energy, follow live-work rules and barriers.', 'ENERGY_ISOLATION', true, true, true),
    ('10000000-0000-0000-0000-000000421002'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400010'::uuid, 2,
     'Instrument PPE', 'Insulated gloves/glasses as required by diagnostic method.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000421003'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400010'::uuid, 3,
     'Keep clear during test', 'Personnel clear of moving/pressurized parts during test.', 'WORKPLACE', true, false, true),
    ('10000000-0000-0000-0000-000000420401'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400004'::uuid, 1,
     'Wear required PPE', 'Gloves, eye protection and task-specific PPE.', 'PPE', true, false, true),
    ('10000000-0000-0000-0000-000000420402'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400004'::uuid, 2,
     'Equipment stopped before mechanical work', 'Do not work on rotating or pressurized equipment while running.', 'ENERGY_ISOLATION', true, false, true),
    ('10000000-0000-0000-0000-000000420403'::uuid, now(), now(), false, '10000000-0000-0000-0000-000000400004'::uuid, 3,
     'Tools and area checked before startup', 'Remove tools and people from danger zone before start.', 'TOOL', false, false, true)
) AS seed(id, created_at, updated_at, is_deleted, template_id, sequence, label, description, category, critical, requires_comment, active)
ON CONFLICT (id) DO UPDATE SET
    updated_at = EXCLUDED.updated_at,
    template_id = EXCLUDED.template_id,
    sequence = EXCLUDED.sequence,
    label = EXCLUDED.label,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    critical = EXCLUDED.critical,
    requires_comment = EXCLUDED.requires_comment,
    active = EXCLUDED.active;

-- ===========================================================================
-- 3) WO flags + task safety text (AI-readable)
-- ===========================================================================
UPDATE work_orders wo
SET requires_isolation = true,
    requires_shutdown = CASE
        WHEN wo.type::text IN ('EMERGENCY', 'DEFECT', 'OVERHAUL', 'MEDIUM_REPAIR', 'CAPITAL_REPAIR') THEN true
        WHEN wo.work_type::text IN ('REPAIR', 'REPLACEMENT') THEN true
        ELSE wo.requires_shutdown
    END,
    updated_at = now()
WHERE wo.is_deleted = false
  AND (wo.requires_isolation = false OR wo.requires_shutdown = false);

UPDATE work_order_tasks wot
SET description = NULLIF(
        CONCAT_WS(E'\n',
            NULLIF(btrim(wot.description), ''),
            CASE WHEN mo.safety_notes IS NOT NULL AND btrim(mo.safety_notes) <> ''
                 THEN 'Safety: ' || mo.safety_notes END,
            CASE WHEN ma.safety_notes IS NOT NULL AND btrim(ma.safety_notes) <> ''
                 AND (mo.safety_notes IS NULL OR position(ma.safety_notes in mo.safety_notes) = 0)
                 THEN 'Action safety: ' || ma.safety_notes END,
            CASE WHEN COALESCE(mo.tools_required, ma.tools_required) IS NOT NULL
                 THEN 'Tools: ' || COALESCE(mo.tools_required, ma.tools_required) END,
            CASE WHEN COALESCE(mo.required_skill, ma.required_skill) IS NOT NULL
                 THEN 'Required skill: ' || COALESCE(mo.required_skill, ma.required_skill) END
        ),
        ''
    ),
    updated_at = now()
FROM maintenance_operations mo
LEFT JOIN maintenance_actions ma ON ma.id = mo.action_id AND ma.is_deleted = false
WHERE wot.is_deleted = false
  AND wot.source_operation_id = mo.id
  AND mo.is_deleted = false
  AND (wot.description IS NULL OR wot.description NOT ILIKE '%Safety:%');

UPDATE work_order_tasks wot
SET description = CONCAT_WS(E'\n',
        NULLIF(btrim(wot.description), ''),
        CASE
            WHEN wot.title ILIKE '%isolat%' OR wot.title ILIKE '%LOTO%' OR wot.title ILIKE '%drain%' THEN
                'Safety: Apply LOTO and verify zero energy before opening equipment. Depressurize and drain safely into containment.'
            WHEN wot.title ILIKE '%seal%' THEN
                'Safety: Wear chemical-resistant gloves. Depressurize before removing seal cartridge. Contain spills.'
            WHEN wot.title ILIKE '%bearing%' OR wot.title ILIKE '%replace part%' THEN
                'Safety: Isolate drive, lock shaft, use correct puller/heater. Wear heat-resistant gloves for hot bearings.'
            WHEN wot.title ILIKE '%inspect%' OR wot.title ILIKE '%vibration%' OR wot.title ILIKE '%diagnos%' THEN
                'Safety: Keep clear of rotating parts during running checks. Stop unit before intrusive inspection.'
            WHEN wot.title ILIKE '%test run%' OR wot.title ILIKE '%assembly%' OR wot.title ILIKE '%handover%' THEN
                'Safety: Remove tools, restore guards, clear remote-start zone before test run.'
            WHEN wot.title ILIKE '%electr%' OR wot.title ILIKE '%calibrat%' THEN
                'Safety: Prove dead before contact. Use insulated tools/gloves. Follow live-work barriers if energized diagnostics required.'
            WHEN wot.title ILIKE '%clean%' OR wot.title ILIKE '%heat%' THEN
                'Safety: Isolate both sides, vent pressure, use chemical PPE for cleaners. Confined-space permit if entering.'
            ELSE
                'Safety: Wear required PPE. Stop and isolate equipment before mechanical work. Confirm permit and area clearance.'
        END,
        CASE
            WHEN wot.title ILIKE '%isolat%' OR wot.title ILIKE '%drain%' THEN 'Tools: LOTO kit, drain pan, wrenches'
            WHEN wot.title ILIKE '%seal%' THEN 'Tools: Puller, torque wrench, dial indicator'
            WHEN wot.title ILIKE '%inspect%' OR wot.title ILIKE '%vibration%' THEN 'Tools: Vibration meter, IR thermometer'
            WHEN wot.title ILIKE '%test run%' THEN 'Tools: Checklist, tachometer/gauges as required'
            ELSE 'Tools: Standard workshop toolbox'
        END,
        'Required skill: MECHANIC'
    ),
    updated_at = now()
WHERE wot.is_deleted = false
  AND (wot.description IS NULL OR wot.description NOT ILIKE '%Safety:%');

UPDATE work_order_tasks
SET description = CONCAT_WS(E'\n',
        'Apply lockout and drain the pump casing.',
        'Safety: Apply LOTO and verify zero energy before opening the pump casing. Place drip trays; wear oil-resistant gloves and glasses.',
        'Tools: LOTO kit, drain pan, wrench set',
        'Required skill: MECHANIC'
    ),
    updated_at = now()
WHERE id = '00000000-0000-0000-0000-000000080101' AND is_deleted = false;

UPDATE work_order_tasks
SET description = CONCAT_WS(E'\n',
        'Replace mechanical seal and inspect shaft sleeve.',
        'Safety: Wear chemical-resistant gloves. Depressurize before removing the seal cartridge. Do not reuse damaged seal faces.',
        'Tools: Puller, torque wrench, dial indicator',
        'Required skill: MECHANIC',
        'Action safety: Depressurize and drain the line. Use chemical-resistant gloves for process fluid. Do not reuse damaged seal faces; inspect shaft sleeve before assembly.'
    ),
    updated_at = now()
WHERE id = '00000000-0000-0000-0000-000000080102' AND is_deleted = false;

-- ===========================================================================
-- 4) Backfill WO safety checklists + items for all work orders missing them
-- ===========================================================================
WITH ranked AS (
    SELECT
        wo.id AS work_order_id,
        tpl.id AS template_id,
        CASE
            WHEN tpl.work_order_type IS NOT NULL AND tpl.work_order_type = wo.type::text THEN 1
            WHEN tpl.work_order_type IS NULL AND tpl.work_type IS NOT NULL AND tpl.work_type = wo.work_type::text THEN 2
            WHEN tpl.work_order_type IS NULL AND tpl.work_type IS NULL THEN 3
            ELSE 9
        END AS rank_no,
        tpl.updated_at
    FROM work_orders wo
    JOIN safety_checklist_templates tpl
      ON tpl.is_deleted = false
     AND tpl.active = true
     AND (
            (tpl.work_order_type IS NOT NULL AND tpl.work_order_type = wo.type::text)
         OR (tpl.work_order_type IS NULL AND tpl.work_type IS NOT NULL AND tpl.work_type = wo.work_type::text)
         OR (tpl.work_order_type IS NULL AND tpl.work_type IS NULL)
     )
    WHERE wo.is_deleted = false
),
wo_template AS (
    SELECT DISTINCT ON (work_order_id)
        work_order_id, template_id
    FROM ranked
    WHERE rank_no < 9
    ORDER BY work_order_id, rank_no, updated_at DESC
),
inserted_checklists AS (
    INSERT INTO work_order_safety_checklists (
        id, created_at, updated_at, is_deleted, work_order_id, template_id, status, checked_by_id, checked_at, remarks
    )
    SELECT
        gen_random_uuid(), now(), now(), false,
        wt.work_order_id, wt.template_id, 'DRAFT', NULL, NULL,
        (
            SELECT NULLIF(string_agg(line, E'\n' ORDER BY line), '')
            FROM (
                SELECT DISTINCT unnest(string_to_array(wot.description, E'\n')) AS line
                FROM work_order_tasks wot
                WHERE wot.work_order_id = wt.work_order_id
                  AND wot.is_deleted = false
                  AND wot.description IS NOT NULL
                  AND (wot.description ILIKE '%Safety:%' OR wot.description ILIKE '%Tools:%' OR wot.description ILIKE '%Action safety:%')
            ) remarks
            WHERE btrim(line) <> ''
              AND (line ILIKE 'Safety:%' OR line ILIKE 'Tools:%' OR line ILIKE 'Action safety:%' OR line ILIKE 'Required skill:%')
        )
    FROM wo_template wt
    WHERE NOT EXISTS (
        SELECT 1 FROM work_order_safety_checklists existing
        WHERE existing.work_order_id = wt.work_order_id
          AND existing.is_deleted = false
          AND existing.status <> 'CANCELLED'
    )
    RETURNING id, template_id, work_order_id
)
INSERT INTO work_order_safety_checklist_items (
    id, created_at, updated_at, is_deleted, checklist_id, template_item_id,
    sequence, label, category, critical, requires_comment, status, comment, checked_by_id, checked_at
)
SELECT
    gen_random_uuid(), now(), now(), false,
    ic.id, sti.id, sti.sequence, sti.label, sti.category,
    sti.critical, sti.requires_comment, 'PENDING', NULL, NULL, NULL
FROM inserted_checklists ic
JOIN safety_checklist_template_items sti
  ON sti.template_id = ic.template_id
 AND sti.is_deleted = false
 AND sti.active = true;

-- Refresh remarks on existing DRAFT checklists from current task safety text
UPDATE work_order_safety_checklists c
SET remarks = src.remarks,
    updated_at = now()
FROM (
    SELECT
        c2.id AS checklist_id,
        (
            SELECT NULLIF(string_agg(line, E'\n' ORDER BY line), '')
            FROM (
                SELECT DISTINCT unnest(string_to_array(wot.description, E'\n')) AS line
                FROM work_order_tasks wot
                WHERE wot.work_order_id = c2.work_order_id
                  AND wot.is_deleted = false
                  AND wot.description IS NOT NULL
            ) lines
            WHERE btrim(line) <> ''
              AND (line ILIKE 'Safety:%' OR line ILIKE 'Tools:%' OR line ILIKE 'Action safety:%' OR line ILIKE 'Required skill:%')
        ) AS remarks
    FROM work_order_safety_checklists c2
    WHERE c2.is_deleted = false
      AND c2.status = 'DRAFT'
) src
WHERE c.id = src.checklist_id
  AND src.remarks IS NOT NULL
  AND (c.remarks IS NULL OR btrim(c.remarks) = '' OR c.remarks <> src.remarks);

-- Add any missing template items onto existing DRAFT checklists
INSERT INTO work_order_safety_checklist_items (
    id, created_at, updated_at, is_deleted, checklist_id, template_item_id,
    sequence, label, category, critical, requires_comment, status, comment, checked_by_id, checked_at
)
SELECT
    gen_random_uuid(), now(), now(), false,
    c.id, sti.id, sti.sequence, sti.label, sti.category,
    sti.critical, sti.requires_comment, 'PENDING', NULL, NULL, NULL
FROM work_order_safety_checklists c
JOIN safety_checklist_template_items sti
  ON sti.template_id = c.template_id
 AND sti.is_deleted = false
 AND sti.active = true
WHERE c.is_deleted = false
  AND c.status = 'DRAFT'
  AND c.template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM work_order_safety_checklist_items existing
      WHERE existing.checklist_id = c.id
        AND existing.is_deleted = false
        AND (
            existing.template_item_id = sti.id
            OR (existing.sequence = sti.sequence AND existing.label = sti.label)
        )
  );
