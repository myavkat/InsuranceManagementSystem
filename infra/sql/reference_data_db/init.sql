CREATE TABLE IF NOT EXISTS cities (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    plate_code VARCHAR(2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS professions (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cities_name ON cities(name);
CREATE INDEX IF NOT EXISTS idx_professions_name ON professions(name);

-- Seed all 81 Turkish cities
INSERT INTO cities (id, name, plate_code) VALUES
(1, 'Adana', '01'), (2, 'Adıyaman', '02'), (3, 'Afyonkarahisar', '03'), (4, 'Ağrı', '04'),
(5, 'Amasya', '05'), (6, 'Ankara', '06'), (7, 'Antalya', '07'), (8, 'Artvin', '08'),
(9, 'Aydın', '09'), (10, 'Balıkesir', '10'), (11, 'Bilecik', '11'), (12, 'Bingöl', '12'),
(13, 'Bitlis', '13'), (14, 'Bolu', '14'), (15, 'Burdur', '15'), (16, 'Bursa', '16'),
(17, 'Çanakkale', '17'), (18, 'Çankırı', '18'), (19, 'Çorum', '19'), (20, 'Denizli', '20'),
(21, 'Diyarbakır', '21'), (22, 'Edirne', '22'), (23, 'Elazığ', '23'), (24, 'Erzincan', '24'),
(25, 'Erzurum', '25'), (26, 'Eskişehir', '26'), (27, 'Gaziantep', '27'), (28, 'Giresun', '28'),
(29, 'Gümüşhane', '29'), (30, 'Hakkâri', '30'), (31, 'Hatay', '31'), (32, 'Isparta', '32'),
(33, 'Mersin', '33'), (34, 'İstanbul', '34'), (35, 'İzmir', '35'), (36, 'Kars', '36'),
(37, 'Kastamonu', '37'), (38, 'Kayseri', '38'), (39, 'Kırklareli', '39'), (40, 'Kırşehir', '40'),
(41, 'Kocaeli', '41'), (42, 'Konya', '42'), (43, 'Kütahya', '43'), (44, 'Malatya', '44'),
(45, 'Manisa', '45'), (46, 'Kahramanmaraş', '46'), (47, 'Mardin', '47'), (48, 'Muğla', '48'),
(49, 'Muş', '49'), (50, 'Nevşehir', '50'), (51, 'Niğde', '51'), (52, 'Ordu', '52'),
(53, 'Rize', '53'), (54, 'Sakarya', '54'), (55, 'Samsun', '55'), (56, 'Siirt', '56'),
(57, 'Sinop', '57'), (58, 'Sivas', '58'), (59, 'Tekirdağ', '59'), (60, 'Tokat', '60'),
(61, 'Trabzon', '61'), (62, 'Tunceli', '62'), (63, 'Şanlıurfa', '63'), (64, 'Uşak', '64'),
(65, 'Van', '65'), (66, 'Yozgat', '66'), (67, 'Zonguldak', '67'), (68, 'Aksaray', '68'),
(69, 'Bayburt', '69'), (70, 'Karaman', '70'), (71, 'Kırıkkale', '71'), (72, 'Batman', '72'),
(73, 'Şırnak', '73'), (74, 'Bartın', '74'), (75, 'Ardahan', '75'), (76, 'Iğdır', '76'),
(77, 'Yalova', '77'), (78, 'Karabük', '78'), (79, 'Kilis', '79'), (80, 'Osmaniye', '80'),
(81, 'Düzce', '81')
ON CONFLICT (id) DO NOTHING;

-- Seed 35 professions
INSERT INTO professions (id, name) VALUES
(1, 'Doctor'), (2, 'Engineer'), (3, 'Teacher'), (4, 'Lawyer'), (5, 'Accountant'),
(6, 'Architect'), (7, 'Dentist'), (8, 'Pharmacist'), (9, 'Nurse'), (10, 'Police Officer'),
(11, 'Firefighter'), (12, 'Farmer'), (13, 'Business Owner'), (14, 'Software Developer'),
(15, 'Manager'), (16, 'Consultant'), (17, 'Journalist'), (18, 'Pilot'), (19, 'Chef'),
(20, 'Electrician'), (21, 'Plumber'), (22, 'Carpenter'), (23, 'Driver'), (24, 'Student'),
(25, 'Retired'), (26, 'Housewife'), (27, 'Military Personnel'), (28, 'Judge'),
(29, 'Economist'), (30, 'Psychologist'), (31, 'Veterinarian'), (32, 'Artist'),
(33, 'Real Estate Agent'), (34, 'Banker'), (35, 'Civil Servant')
ON CONFLICT (id) DO NOTHING;
