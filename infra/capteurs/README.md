# Capteurs de comptage (laser) — Arduino / Raspberry

Un capteur compte les passages **sans billet**. Il envoie chaque passage à la plateforme ;
le total s'affiche dans « Présence / Flux » sous « Comptage physique (capteurs laser) »,
séparément des scans de billets.

## 1. Créer le capteur (une fois par boîtier)

Éditeur de l'événement → onglet **Contrôle** → *Capteurs de comptage* → nom (ex. « Porte principale ») → **Créer**.
La clé `pne_...` n'est affichée **qu'une seule fois** : la copier dans le boîtier. Elle est révocable
(bouton *Révoquer*) et ne fonctionne que pour cet événement.

## 2. Endpoints (sans compte, clé dans l'en-tête)

| Action | Requête |
|---|---|
| Une **entrée** | `POST /api/sensors/entry` |
| Une **sortie** | `POST /api/sensors/exit` |
| Plusieurs d'un coup (réseau coupé) | `POST /api/sensors/entry?count=5` (1 à 1000) |

En-tête obligatoire : `X-Sensor-Key: pne_...` — pas de corps de requête.
Réponse (`200`) : `{"entrees": 12, "sorties": 4, "presents": 8}`.
Erreurs : `401` clé absente/invalide/révoquée · `422 EVENT_NOT_ACTIVE` événement pas encore publié ·
`422 INVALID_COUNT` `count` hors 1–1000.

Test rapide : `curl -X POST -H "X-Sensor-Key: pne_..." https://<domaine>/api/sensors/entry`

Un faisceau = un sens. Pour compter entrée **et** sortie, utiliser deux faisceaux (deux voies) : le premier
appelle `/entry`, le second `/exit`. Un même boîtier peut porter les deux avec la même clé.

## 3. ESP32 / ESP8266 (Arduino IDE)

Un Arduino Uno n'a pas de Wi-Fi : utiliser un ESP32/ESP8266 (ou un shield Ethernet/Wi-Fi).
Récepteur laser (module photodiode) : sortie LOW quand le faisceau est coupé.

```cpp
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>

const char* WIFI_SSID = "...";
const char* WIFI_PASS = "...";
const String BASE = "https://evenements-19.mtdpce-test.gov.bf/api/sensors";
const char* KEY = "pne_...";               // clé du capteur

const int PIN_ENTRY = 18;                  // faisceau « entrée »
const int PIN_EXIT  = 19;                  // faisceau « sortie »
const unsigned long DEBOUNCE_MS = 400;     // un passage = une coupure > 400 ms d'écart

volatile unsigned long lastEntry = 0, lastExit = 0;
volatile int pendingEntry = 0, pendingExit = 0;

void IRAM_ATTR onEntry() { unsigned long t = millis(); if (t - lastEntry > DEBOUNCE_MS) { lastEntry = t; pendingEntry++; } }
void IRAM_ATTR onExit()  { unsigned long t = millis(); if (t - lastExit  > DEBOUNCE_MS) { lastExit  = t; pendingExit++;  } }

bool send(const String& what, int count) {
  WiFiClientSecure client;
  client.setInsecure();                    // ou client.setCACert(...) en production
  HTTPClient http;
  http.begin(client, BASE + "/" + what + "?count=" + count);
  http.addHeader("X-Sensor-Key", KEY);
  int code = http.POST("");
  http.end();
  return code == 200;
}

void setup() {
  pinMode(PIN_ENTRY, INPUT_PULLUP);
  pinMode(PIN_EXIT, INPUT_PULLUP);
  attachInterrupt(PIN_ENTRY, onEntry, FALLING);
  attachInterrupt(PIN_EXIT,  onExit,  FALLING);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(500);
}

void loop() {
  // envoi groupé toutes les secondes ; en cas d'échec le compteur est conservé et renvoyé
  int e = pendingEntry, x = pendingExit;
  if (e > 0 && send("entry", e)) pendingEntry -= e;
  if (x > 0 && send("exit",  x)) pendingExit  -= x;
  delay(1000);
}
```

## 4. Raspberry Pi (Python)

```python
import time, requests
from gpiozero import DigitalInputDevice

BASE = "https://evenements-19.mtdpce-test.gov.bf/api/sensors"
HEAD = {"X-Sensor-Key": "pne_..."}
pending = {"entry": 0, "exit": 0}

def on_break(kind):
    pending[kind] += 1

# capteur actif à LOW quand le faisceau est coupé ; bounce_time = anti-rebond
DigitalInputDevice(17, pull_up=True, bounce_time=0.4).when_deactivated = lambda: on_break("entry")
DigitalInputDevice(27, pull_up=True, bounce_time=0.4).when_deactivated = lambda: on_break("exit")

while True:
    for kind in ("entry", "exit"):
        n = pending[kind]
        if n:
            try:
                r = requests.post(f"{BASE}/{kind}", params={"count": n}, headers=HEAD, timeout=5)
                if r.status_code == 200:
                    pending[kind] -= n
            except requests.RequestException:
                pass          # réseau coupé : on garde le compteur et on réessaie
    time.sleep(1)
```

## Bon à savoir

- Un laser compte aussi les personnes sans billet et les doubles passages : ce chiffre ne se mélange
  volontairement pas aux scans de billets.
- Les passages ne sont acceptés que lorsque l'événement est publié ou en cours.
- Le certificat du domaine de test est interne : `setInsecure()` (ESP32) / `verify=False` (Python)
  peut être nécessaire en test ; en production, fournir le certificat CA.
