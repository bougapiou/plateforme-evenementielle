-- Le sens du contrôle (entrée / sortie) est désormais choisi par l'agent à
-- chaque scan ; le drapeau par événement n'a plus de raison d'être.
alter table events drop column controle_sortie;
