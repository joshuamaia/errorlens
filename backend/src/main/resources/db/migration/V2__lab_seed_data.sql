-- ===========================================================================
-- Dados demonstrativos do laboratório (fictícios)
-- ===========================================================================

INSERT INTO customers (name, email, document, status, loyalty_tier, credit_limit, origin) VALUES
    ('Ana Souza',        'ana.souza@errorlens.dev',        '111.444.777-35', 'ACTIVE',   'GOLD',     15000.00, 'SEED'),
    ('Bruno Lima',       'bruno.lima@errorlens.dev',       '222.555.888-46', 'ACTIVE',   'SILVER',    8000.00, 'SEED'),
    ('Carla Mendes',     'carla.mendes@errorlens.dev',     '333.666.999-57', 'ACTIVE',   NULL,        2500.00, 'SEED'),
    ('Diego Ferreira',   'diego.ferreira@errorlens.dev',   '444.777.000-68', 'ACTIVE',   'BRONZE',    3000.00, 'SEED'),
    ('Eduarda Martins',  'eduarda.martins@errorlens.dev',  '555.888.111-79', 'INACTIVE', 'SILVER',    6000.00, 'SEED'),
    ('Felipe Rocha',     'felipe.rocha@errorlens.dev',     '666.999.222-80', 'ACTIVE',   'GOLD',     20000.00, 'SEED'),
    ('Gabriela Alves',   'gabriela.alves@errorlens.dev',   '777.000.333-91', 'ACTIVE',   'BRONZE',    1500.00, 'SEED'),
    ('Henrique Castro',  'henrique.castro@errorlens.dev',  '888.111.444-02', 'BLOCKED',  NULL,           0.00, 'SEED'),
    ('Isabela Ribeiro',  'isabela.ribeiro@errorlens.dev',  '999.222.555-13', 'ACTIVE',   'SILVER',    7000.00, 'SEED'),
    ('João Carvalho',    'joao.carvalho@errorlens.dev',    '012.345.678-90', 'ACTIVE',   'GOLD',     12000.00, 'SEED'),
    ('Karina Duarte',    'karina.duarte@errorlens.dev',    '123.456.789-09', 'ACTIVE',   'BRONZE',    2000.00, 'SEED'),
    ('Lucas Pereira',    'lucas.pereira@errorlens.dev',    '234.567.890-12', 'ACTIVE',   'SILVER',    5000.00, 'SEED');

-- Pedidos associados (Ana Souza possui pedidos -> usado no cenário D02 de chave estrangeira)
INSERT INTO orders (customer_id, description, amount, status, invoice_metadata)
SELECT c.id, o.description, o.amount, o.status, o.invoice_metadata
FROM (VALUES
    ('ana.souza@errorlens.dev',       'Notebook Pro 14',            8999.90, 'PAID',      'parcelas=10'),
    ('ana.souza@errorlens.dev',       'Monitor UltraWide 34',       3299.00, 'SHIPPED',   'parcelas=6'),
    ('bruno.lima@errorlens.dev',      'Teclado mecânico',            649.90, 'CREATED',   'parcelas=três'),
    ('felipe.rocha@errorlens.dev',    'Cadeira ergonômica',         1899.00, 'PAID',      'parcelas=3'),
    ('isabela.ribeiro@errorlens.dev', 'Headset sem fio',             899.00, 'CANCELLED', 'parcelas=1'),
    ('joao.carvalho@errorlens.dev',   'Dock station USB-C',          1299.00, 'PAID',      'parcelas=2'),
    ('joao.carvalho@errorlens.dev',   'SSD NVMe 2TB',                1199.00, 'SHIPPED',   'parcelas=4'),
    ('lucas.pereira@errorlens.dev',   'Webcam 4K',                    799.00, 'CREATED',   'parcelas=2')
) AS o(email, description, amount, status, invoice_metadata)
JOIN customers c ON c.email = o.email;
