-- =====================================================================
-- Dummy but realistic data for schema v2 (local development only).
-- Deterministic: setseed() fixes random(), so every rebuild gives the same figures
-- (uuids from gen_random_uuid() still differ). Dates are 2026 up to now().
--
--   * 2 farms, 5 barns, 482 head (Lai Sind-heavy Vietnamese beef mix)
--   * ~1 000 sporadic health events, more respiratory / diarrhoea / blood-parasite cases
--     in the rainy season (Jun–Oct), 3 vets, realistic drugs, doses and lot numbers
--   * outbreak clusters: suspected FMD DLK-B01 (Mar), LSD GLI-B02 (Jul), HS DLK-B02 (Nov)
--   * vaccination campaigns (FMD x2, HS, LSD), a few corrected / voided entries
--   * November gate traffic: daily pasture release + return per barn, missed reads entered
--     manually, unknown tags, the odd missing animal with its alert
-- =====================================================================
SELECT setseed(0.2026);

-- ------------------------------------------------------------------ farms, barns, gates, readers
INSERT INTO barn_gate.farm (farm_id, name, province) VALUES
    ('GLI', 'Trang trại VinaBeef Gia Lai', 'Gia Lai'),
    ('DLK', 'Trang trại VinaBeef Đắk Lắk', 'Đắk Lắk');

INSERT INTO barn_gate.barn (barn_id, farm_id, capacity) VALUES
    ('GLI-B01', 'GLI', 120), ('GLI-B02', 'GLI', 120), ('GLI-B03', 'GLI', 80),
    ('DLK-B01', 'DLK', 150), ('DLK-B02', 'DLK', 100);

INSERT INTO barn_gate.gate (gate_id, barn_id, gate_type, leads_to) VALUES
    ('GLI-B01-G1', 'GLI-B01', 'BIDIR', 'PASTURE'),
    ('GLI-B02-G1', 'GLI-B02', 'BIDIR', 'PASTURE'),
    ('GLI-B03-G1', 'GLI-B03', 'BIDIR', 'PASTURE'),
    ('DLK-B01-G1', 'DLK-B01', 'BIDIR', 'PASTURE'),
    ('DLK-B02-G1', 'DLK-B02', 'BIDIR', 'YARD');      -- feedlot barn: no daily pasture sessions

INSERT INTO barn_gate.reader_station (gate_id, serial_no, model, status, active_config_ver, last_heartbeat_at)
SELECT gate_id, 'RS-' || replace(gate_id, '-', '') || '-0' || (row_number() OVER ()), 'Allflex RS420',
       CASE WHEN gate_id = 'DLK-B02-G1' THEN 'OFFLINE' ELSE 'ONLINE' END, 1,
       now() - CASE WHEN gate_id = 'DLK-B02-G1' THEN interval '3 hours' ELSE interval '40 seconds' END
FROM barn_gate.gate;

-- ------------------------------------------------------------------ users
INSERT INTO security.app_user (user_id, idp_subject, username, full_name, email, phone, status) VALUES
    ('00000000-0000-0000-0000-00000000a001', 'kc-1001', 'khoa.tran',   'Trần Minh Khoa',   'khoa.tran@example.invalid',   '0905 112 301', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000a002', 'kc-1002', 'hong.le',     'Lê Thị Hồng',      'hong.le@example.invalid',     '0905 112 302', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000a003', 'kc-1003', 'bao.pham',    'Phạm Quốc Bảo',    'bao.pham@example.invalid',    '0912 448 210', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000a010', 'kc-1010', 'lan.nguyen',  'Nguyễn Thị Lan',   'lan.nguyen@example.invalid',  '0903 776 015', 'ACTIVE');

-- ------------------------------------------------------------------ cattle and tags
-- Herd per barn: GLI-B01 110, GLI-B02 104, GLI-B03 68, DLK-B01 138, DLK-B02 62  (= 482)
CREATE TEMP TABLE seed_animal AS
SELECT n,
       ('00000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid AS animal_id,
       CASE WHEN n <= 110 THEN 'GLI-B01' WHEN n <= 214 THEN 'GLI-B02' WHEN n <= 282 THEN 'GLI-B03'
            WHEN n <= 420 THEN 'DLK-B01' ELSE 'DLK-B02' END              AS barn_id,
       '7040010' || lpad((n * 7919 % 99999989)::text, 8, '0')            AS rfid_tag,
       random() AS r_breed, random() AS r_sex, random() AS r_status, random() AS r_age, random() AS r_origin
FROM generate_series(1, 482) n;

INSERT INTO cattle_tag.cattle (animal_id, expected_barn_id, breed, sex, birth_date, origin, status, created_at, updated_at)
SELECT animal_id, barn_id,
       CASE WHEN r_breed < 0.38 THEN 'Lai Sind'      WHEN r_breed < 0.58 THEN 'Brahman'
            WHEN r_breed < 0.72 THEN 'Droughtmaster' WHEN r_breed < 0.84 THEN 'BBB lai'
            WHEN r_breed < 0.93 THEN 'Red Angus'     ELSE 'Charolais lai' END,
       CASE WHEN r_sex < 0.62 THEN 'MALE' ELSE 'FEMALE' END,
       DATE '2026-11-16' - (240 + (r_age * 900)::int),
       CASE WHEN r_origin < 0.35 THEN 'PURCHASED' ELSE 'BORN' END,
       CASE WHEN r_status < 0.050 THEN 'SOLD' WHEN r_status < 0.058 THEN 'DEAD' ELSE 'ACTIVE' END,
       now() - interval '330 days', now() - (r_status * interval '60 days')
FROM seed_animal;

INSERT INTO cattle_tag.rfid_tag (rfid_tag, batch_no, status)
SELECT rfid_tag, 'ALF-2025-0' || (1 + n % 3), 'ACTIVE' FROM seed_animal;
INSERT INTO cattle_tag.tag_assignment (animal_id, rfid_tag, assigned_at)
SELECT animal_id, rfid_tag, now() - interval '320 days' + (n % 40) * interval '1 day' FROM seed_animal
WHERE n % 50 <> 0;

-- Every 50th animal lost its first ear tag in spring and was re-tagged (reports must show the new one).
INSERT INTO cattle_tag.rfid_tag (rfid_tag, batch_no, status)
SELECT '7040020' || lpad((n * 104729 % 99999989)::text, 8, '0'), 'ALF-2026-01', 'ACTIVE' FROM seed_animal WHERE n % 50 = 0;
UPDATE cattle_tag.rfid_tag SET status = 'LOST'
WHERE rfid_tag IN (SELECT rfid_tag FROM seed_animal WHERE n % 50 = 0);
INSERT INTO cattle_tag.tag_assignment (animal_id, rfid_tag, assigned_at, unassigned_at, reason)
SELECT animal_id, rfid_tag, now() - interval '320 days', TIMESTAMPTZ '2026-04-02 09:30+07', 'LOST'
FROM seed_animal WHERE n % 50 = 0;
INSERT INTO cattle_tag.tag_assignment (animal_id, rfid_tag, assigned_at)
SELECT animal_id, '7040020' || lpad((n * 104729 % 99999989)::text, 8, '0'), TIMESTAMPTZ '2026-04-02 10:15+07'
FROM seed_animal WHERE n % 50 = 0;

-- ------------------------------------------------------------------ health: sporadic cases
-- Cumulative weights per season: dry = Nov–May, wet = Jun–Oct (more BRD, diarrhoea, blood parasites)
CREATE TEMP TABLE dx (
    dry_lo numeric, dry_hi numeric, wet_lo numeric, wet_hi numeric,
    kind varchar, diagnosis varchar, disease_code varchar, drug varchar, dose_lo int, dose_hi int, dose_unit varchar
);
INSERT INTO dx VALUES
 (0.00, 0.22, 0.00, 0.30, 'TREATMENT',   'Viêm phổi (BRD)',                     NULL, 'Florfenicol 30% (Nuflor)',                    30,  60, 'ml'),
 (0.22, 0.36, 0.30, 0.48, 'TREATMENT',   'Tiêu chảy',                            NULL, 'Enrofloxacin 10% + điện giải',                15,  30, 'ml'),
 (0.36, 0.46, 0.48, 0.60, 'TREATMENT',   'Ký sinh trùng đường máu (Babesia)',    NULL, 'Diminazene aceturate (Berenil)',              10,  20, 'ml'),
 (0.46, 0.56, 0.60, 0.70, 'TREATMENT',   'Viêm móng',                            NULL, 'Oxytetracycline LA 20% + sát trùng móng',     20,  40, 'ml'),
 (0.56, 0.66, 0.70, 0.75, 'TREATMENT',   'Chướng hơi dạ cỏ',                     NULL, 'Dimethicone (uống)',                         100, 200, 'ml'),
 (0.66, 0.70, 0.75, 0.80, 'TREATMENT',   'Tụ huyết trùng',                       'HS', 'Oxytetracycline LA 20%',                      25,  45, 'ml'),
 (0.70, 0.76, 0.80, 0.84, 'TREATMENT',   'Viêm kết mạc mắt',                     NULL, 'Mỡ tra mắt Tetracycline 1%',                   1,   2, 'tuýp'),
 (0.76, 0.82, 0.84, 0.88, 'TREATMENT',   'Vết thương ngoài da',                  NULL, 'Penicillin-Streptomycin + xịt sát trùng',     10,  20, 'ml'),
 (0.82, 0.94, 0.88, 0.95, 'EXAMINATION', 'Khám định kỳ – bình thường',           NULL, NULL,                                         NULL, NULL, NULL),
 (0.94, 1.01, 0.95, 1.01, 'EXAMINATION', 'Sụt cân – theo dõi dinh dưỡng',        NULL, NULL,                                         NULL, NULL, NULL);

CREATE TEMP TABLE seed_event AS
SELECT d::date AS day, slot,
       1 + floor(random() * 482)::int        AS animal_n,
       random()                              AS r_dx,
       random()                              AS r_time,
       random()                              AS r_dose,
       random()                              AS r_flag,
       extract(month FROM d) BETWEEN 6 AND 10 AS wet
FROM generate_series(DATE '2026-01-01', DATE '2026-11-16', interval '1 day') d
CROSS JOIN generate_series(1, 8) slot
WHERE random() < CASE WHEN extract(month FROM d) BETWEEN 6 AND 10 THEN 0.44 ELSE 0.30 END;

INSERT INTO health.health_record (animal_id, barn_id, kind, diagnosis, disease_code, outbreak_suspected,
                                  vaccine_or_drug, lot_no, dose, notes, performed_at, performed_by)
SELECT a.animal_id, a.barn_id, x.kind, x.diagnosis,
       x.disease_code,
       x.disease_code IS NOT NULL AND e.r_flag < 0.35,
       x.drug,
       CASE WHEN x.drug IS NOT NULL THEN upper(left(regexp_replace(x.drug, '[^A-Za-z]', '', 'g'), 3)) || to_char(e.day, 'YYMM') || '-' || (11 + (e.r_flag * 80)::int) END,
       CASE WHEN x.drug IS NOT NULL THEN (x.dose_lo + round(e.r_dose * (x.dose_hi - x.dose_lo)))::int || ' ' || x.dose_unit END,
       CASE WHEN x.kind = 'TREATMENT' AND e.r_flag > 0.85 THEN 'Tái khám sau 3 ngày'
            WHEN x.kind = 'TREATMENT' AND e.r_flag < 0.08 THEN 'Sốt ' || (39.6 + round((e.r_dose * 1.2)::numeric, 1)) || '°C' END,
       (e.day + time '06:30' + e.r_time * interval '10 hours') AT TIME ZONE 'Asia/Ho_Chi_Minh',
       CASE WHEN a.barn_id LIKE 'DLK%' THEN '00000000-0000-0000-0000-00000000a003'::uuid
            WHEN e.r_time < 0.5        THEN '00000000-0000-0000-0000-00000000a001'::uuid
            ELSE                            '00000000-0000-0000-0000-00000000a002'::uuid END
FROM seed_event e
JOIN seed_animal a ON a.n = e.animal_n
JOIN dx x ON CASE WHEN e.wet THEN e.r_dx >= x.wet_lo AND e.r_dx < x.wet_hi
                  ELSE e.r_dx >= x.dry_lo AND e.r_dx < x.dry_hi END
WHERE (e.day + time '06:30' + e.r_time * interval '10 hours') AT TIME ZONE 'Asia/Ho_Chi_Minh' < now();

-- ------------------------------------------------------------------ health: outbreak clusters
-- (barn, first animal n, last animal n, first day, days, cases, diagnosis, code, drug, note, vet)
CREATE TEMP TABLE outbreak AS SELECT * FROM (VALUES
 ('DLK-B01', 283, 420, DATE '2026-03-02',  6,  7, 'Lở mồm long móng (nghi LMLM)', 'FMD',
  'Povidone-iodine sát trùng miệng-móng + Vitamin C', 'Mụn nước ở miệng và kẽ móng; đã cách ly, báo thú y huyện', '00000000-0000-0000-0000-00000000a003'),
 ('GLI-B02', 111, 214, DATE '2026-07-08', 19, 21, 'Viêm da nổi cục (nghi LSD)', 'LSD',
  'Penicillin-Streptomycin + Meloxicam', 'Nốt sần toàn thân, sốt 40,5°C; cách ly ô số 3, phun diệt côn trùng', '00000000-0000-0000-0000-00000000a001'),
 ('DLK-B02', 421, 482, DATE '2026-11-10',  5,  4, 'Tụ huyết trùng (nghi)', 'HS',
  'Oxytetracycline LA 20%', 'Sốt cao, sưng hầu; lấy mẫu gửi Chi cục Thú y', '00000000-0000-0000-0000-00000000a003')
) AS o(barn_id, n_lo, n_hi, first_day, days, cases, diagnosis, code, drug, note, vet);

INSERT INTO health.health_record (animal_id, barn_id, kind, diagnosis, disease_code, outbreak_suspected,
                                  vaccine_or_drug, lot_no, dose, notes, performed_at, performed_by)
SELECT a.animal_id, o.barn_id, 'TREATMENT', o.diagnosis, o.code, true, o.drug,
       o.code || to_char(o.first_day, 'YYMM') || '-0' || (1 + c % 3),
       (20 + (c * 7) % 25) || ' ml', o.note,
       (o.first_day + (c * o.days / o.cases) + time '07:00' + ((c * 37) % 540) * interval '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh',
       o.vet::uuid
FROM outbreak o
CROSS JOIN LATERAL generate_series(0, o.cases - 1) c
JOIN seed_animal a ON a.n = o.n_lo + (c * 17) % (o.n_hi - o.n_lo + 1)
WHERE (o.first_day + (c * o.days / o.cases) + time '07:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' < now();

-- ------------------------------------------------------------------ health: vaccination campaigns
CREATE TEMP TABLE campaign AS SELECT * FROM (VALUES
 (DATE '2026-03-16', 'Aftovax (LMLM type O)',          'AFT2603-07', '2 ml'),
 (DATE '2026-04-20', 'Vắc xin THT trâu bò (Navetco)',  'THT2604-12', '2 ml'),
 (DATE '2026-05-11', 'MEVAC LSD',                      'LSD2605-03', '1 ml'),
 (DATE '2026-09-14', 'Aftovax (LMLM type O)',          'AFT2609-15', '2 ml')
) AS c(start_day, vaccine, lot, dose);

INSERT INTO health.health_record (animal_id, barn_id, kind, vaccine_or_drug, lot_no, dose, performed_at, performed_by)
SELECT a.animal_id, a.barn_id, 'VACCINATION', c.vaccine, c.lot, c.dose,
       (c.start_day + (a.n % 3) + time '07:00' + ((a.n * 13) % 420) * interval '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh',
       CASE WHEN a.barn_id LIKE 'DLK%' THEN '00000000-0000-0000-0000-00000000a003'::uuid
            ELSE '00000000-0000-0000-0000-00000000a002'::uuid END
FROM campaign c
CROSS JOIN seed_animal a
WHERE a.r_status >= 0.058;                         -- animals still on the farm

-- ------------------------------------------------------------------ health: corrections and voids
-- Wrong dose entered → re-entered with the right dose; the original becomes CORRECTED.
WITH old AS (
    SELECT * FROM health.health_record WHERE kind = 'TREATMENT' AND NOT outbreak_suspected AND dose LIKE '% ml'
    ORDER BY performed_at DESC OFFSET 3 LIMIT 4
), upd AS (
    UPDATE health.health_record h SET status = 'CORRECTED' FROM old WHERE h.record_id = old.record_id
)
INSERT INTO health.health_record (animal_id, barn_id, kind, diagnosis, disease_code, outbreak_suspected,
                                  vaccine_or_drug, lot_no, dose, notes, performed_at, performed_by, corrects_record_id, created_at)
SELECT animal_id, barn_id, kind, diagnosis, disease_code, outbreak_suspected, vaccine_or_drug, lot_no,
       (split_part(dose, ' ', 1)::int / 2) || ' ' || split_part(dose, ' ', 2),
       'Sửa liều: nhập nhầm ' || dose, performed_at, performed_by, record_id, performed_at + interval '2 hours'
FROM old;

-- Entered on the wrong animal → voided.
UPDATE health.health_record SET status = 'VOIDED', notes = 'Huỷ: ghi nhầm cá thể'
WHERE record_id IN (SELECT record_id FROM health.health_record
                    WHERE kind = 'EXAMINATION' AND status = 'ACTIVE' ORDER BY performed_at DESC OFFSET 10 LIMIT 2);

-- ------------------------------------------------------------------ movement: November gate traffic
-- Pasture barns release the herd ~06:15 and bring it back ~16:00 (farm local time).
CREATE TEMP TABLE seed_session AS
SELECT gen_random_uuid() AS session_id, b.barn_id, d::date AS day,
       (d::date + time '06:10' + random() * interval '20 minutes') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS released_at,
       (d::date + time '15:50' + random() * interval '30 minutes') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS returned_at
FROM barn_gate.barn b
CROSS JOIN generate_series(DATE '2026-11-01', DATE '2026-11-16', interval '1 day') d
WHERE b.barn_id <> 'DLK-B02'
  AND (d::date + time '06:10') AT TIME ZONE 'Asia/Ho_Chi_Minh' < now();

CREATE TEMP TABLE seed_trip AS
SELECT s.session_id, s.barn_id, s.day, a.animal_id, a.rfid_tag, a.n,
       s.released_at + random() * interval '40 minutes' AS out_at,
       s.returned_at + random() * interval '55 minutes' AS in_at,
       random() AS r_out, random() AS r_in, random() AS r_manual
FROM seed_session s
JOIN seed_animal a ON a.barn_id = s.barn_id AND a.r_status >= 0.058;

-- Sessions first (movements reference them); counters are filled in after the scans
INSERT INTO movement.herd_session (session_id, barn_id, status, opened_at, closed_at, out_count, in_count)
SELECT s.session_id, s.barn_id,
       CASE WHEN s.returned_at + interval '1 hour' < now() THEN 'CLOSED'
            WHEN s.returned_at < now() THEN 'RETURNING' ELSE 'RELEASING' END,
       s.released_at,
       CASE WHEN s.returned_at + interval '1 hour' < now() THEN s.returned_at + interval '1 hour' END,
       0, 0
FROM seed_session s;
-- current tag per animal (re-tagged animals scan with the new tag)
CREATE TEMP TABLE current_tag AS
SELECT animal_id, rfid_tag FROM cattle_tag.tag_assignment WHERE unassigned_at IS NULL;

-- OUT: 95 % of the herd goes out (sick / pregnant animals stay in)
INSERT INTO movement.movement (event_id, occurred_at, received_at, rfid_tag, animal_id, gate_id, barn_id, session_id,
                               direction, classification, source)
SELECT gen_random_uuid(), t.out_at, t.out_at + (t.r_manual * 4) * interval '1 second',
       ct.rfid_tag, t.animal_id, t.barn_id || '-G1', t.barn_id, t.session_id, 'OUT', 'KNOWN', 'RFID'
FROM seed_trip t JOIN current_tag ct USING (animal_id)
WHERE t.r_out < 0.95 AND t.out_at < now();

-- IN: everyone who went out comes back, except ~0.3 % found missing at count; 1.5 % of reads missed → MANUAL entry
INSERT INTO movement.movement (event_id, occurred_at, received_at, rfid_tag, animal_id, gate_id, barn_id, session_id,
                               direction, classification, source)
SELECT gen_random_uuid(), t.in_at,
       CASE WHEN t.r_manual < 0.015 THEN t.in_at + interval '25 minutes' ELSE t.in_at + (t.r_manual * 4) * interval '1 second' END,
       CASE WHEN t.r_manual < 0.015 THEN NULL ELSE ct.rfid_tag END,
       t.animal_id, t.barn_id || '-G1', t.barn_id, t.session_id, 'IN', 'KNOWN',
       CASE WHEN t.r_manual < 0.015 THEN 'MANUAL' ELSE 'RFID' END
FROM seed_trip t JOIN current_tag ct USING (animal_id)
WHERE t.r_out < 0.95 AND t.r_in >= 0.003 AND t.in_at < now();

-- Duplicate reads (animal lingering in the antenna field) and unknown tags (neighbour's stray, damaged tag)
INSERT INTO movement.movement (event_id, occurred_at, rfid_tag, animal_id, gate_id, barn_id, session_id, direction, classification, source)
SELECT gen_random_uuid(), t.in_at + interval '3 seconds', ct.rfid_tag, t.animal_id, t.barn_id || '-G1', t.barn_id, t.session_id,
       'IN', 'DUPLICATE', 'RFID'
FROM seed_trip t JOIN current_tag ct USING (animal_id)
WHERE t.r_out < 0.95 AND t.r_in >= 0.003 AND t.r_manual BETWEEN 0.50 AND 0.52 AND t.in_at < now();

INSERT INTO movement.movement (event_id, occurred_at, rfid_tag, animal_id, gate_id, barn_id, session_id, direction, classification, source)
SELECT gen_random_uuid(), s.returned_at + random() * interval '50 minutes',
       '9990000' || lpad((floor(random() * 99999999))::text, 8, '0'), NULL,
       s.barn_id || '-G1', s.barn_id, s.session_id, 'IN', 'UNKNOWN', 'RFID'
FROM seed_session s
WHERE random() < 0.25 AND s.returned_at < now();

-- Session counters
UPDATE movement.herd_session h SET
    out_count = (SELECT count(*) FROM movement.movement m WHERE m.session_id = h.session_id AND m.direction = 'OUT' AND m.classification = 'KNOWN'),
    in_count  = (SELECT count(*) FROM movement.movement m WHERE m.session_id = h.session_id AND m.direction = 'IN'  AND m.classification = 'KNOWN');

-- ------------------------------------------------------------------ movement alerts
INSERT INTO movement.alert (session_id, barn_id, animal_id, alert_type, severity, status, raised_at, resolved_at, resolved_by)
SELECT t.session_id, t.barn_id, t.animal_id, 'MISSING', 'HIGH',
       CASE WHEN t.day < DATE '2026-11-16' THEN 'RESOLVED' ELSE 'OPEN' END,
       t.in_at + interval '1 hour',
       CASE WHEN t.day < DATE '2026-11-16' THEN t.in_at + interval '15 hours' END,
       CASE WHEN t.day < DATE '2026-11-16' THEN '00000000-0000-0000-0000-00000000a010'::uuid END
FROM seed_trip t
WHERE t.r_out < 0.95 AND t.r_in < 0.003 AND t.in_at + interval '1 hour' < now();

INSERT INTO movement.alert (session_id, barn_id, alert_type, severity, status, raised_at)
SELECT m.session_id, m.barn_id, 'UNKNOWN_TAG', 'MEDIUM', 'ACK', m.occurred_at + interval '5 seconds'
FROM movement.movement m WHERE m.classification = 'UNKNOWN';

INSERT INTO movement.alert (barn_id, alert_type, severity, status, raised_at)
VALUES ('DLK-B02', 'READER_OFFLINE', 'CRITICAL', 'OPEN', now() - interval '2 hours 50 minutes');

ANALYZE;
