-- A real gateway's checkout URL (FasoArzeka's /validorder, with a full JWT
-- access token and a base64-encoded callback URL as query parameters)
-- comfortably exceeds varchar(1000). Unbounded text avoids hitting this again.
alter table payments
    alter column payment_url type text;
