-- Le numéro d'un stand sert aussi d'étiquette renommable (ex. le nom complet
-- d'un exposant institutionnel), ce qui dépasse largement varchar(40).
alter table stands
    alter column numero type varchar(200);
