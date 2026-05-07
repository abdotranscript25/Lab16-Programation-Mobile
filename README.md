# ⏱️ Lab 16 – Service Chronomètre (Foreground + Bound Service)

## 🎯 Objectif du laboratoire

Créer une application Android qui utilise :

| Composant | Description |
|-----------|-------------|
| **Foreground Service** | Service visible avec notification persistante (obligatoire depuis Android 8.0) |
| **Bound Service** | Communication bidirectionnelle entre l'Activity et le Service |
| **Notification** | Affichage du temps en direct même quand l'app est fermée |
| **START_STICKY** | Service redémarre automatiquement si tué par le système |

---

## 🧠 Concepts clés abordés

| Concept | Description |
|---------|-------------|
| **Service** | Composant Android qui s'exécute en arrière-plan sans interface |
| **Foreground Service** | Service avec notification visible (obligatoire depuis Android 8.0) |
| **Bound Service** | Service lié à une Activity, permet la communication bidirectionnelle |
| **Binder** | Mécanisme qui permet à l'Activity d'appeler des méthodes du Service |
| **START_STICKY** | Flag qui redémarre le service automatiquement si le système le tue |
| **NotificationChannel** | Canal de notification requis depuis Android 8.0 (API 26) |
| **ServiceConnection** | Interface pour gérer la connexion/déconnexion au service |
| **ScheduledExecutorService** | Thread sécurisé pour exécuter des tâches périodiques |
| **Handler + Runnable** | Mise à jour de l'UI depuis un thread secondaire |
| **PendingIntent** | Intent différée pour ouvrir l'app depuis la notification |

---

## 🏗️ Architecture de l'application

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  ┌──────────────────────┐        ┌─────────────────────────┐   │
│  │    MainActivity      │        │   ChronometreService    │   │
│  │                      │        │                         │   │
│  │  - Affiche le temps  │──────► │  - Compte les secondes  │   │
│  │  - Bouton Démarrer   │  start │  - Notification live    │   │
│  │  - Bouton Arrêter    │        │  - Tourne en arrière-   │   │
│  │                      │◄─────► │    plan                 │   │
│  │                      │  bind  │                         │   │
│  └──────────────────────┘        └─────────────────────────┘   │
│            │                                  │                 │
│            ▼                                  ▼                 │
│  ┌──────────────────────┐        ┌─────────────────────────┐   │
│  │  Handler + Runnable  │        │   ScheduledExecutor     │   │
│  │  (met à jour l'UI   │        │   (tâche périodique     │   │
│  │   toutes les 500ms)  │        │    toutes les 1 sec)    │   │
│  └──────────────────────┘        └─────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Cycle de vie du Service

```
startForegroundService()
        ↓
onCreate()            ← Une seule fois : initialisation (NotificationManager, Channel)
        ↓
onStartCommand()      ← Cœur du service : startForeground() + startChronometre()
        ↓
startForeground()     ← Rend le service visible (notification obligatoire)
        ↓
startChronometre()    ← Lance le timer (ScheduledExecutorService)
        ↓
scheduleAtFixedRate() ← Toutes les 1 seconde : secondes++ + updateNotification()
        ↓
   [Service actif]    ← Le chrono tourne même si l'app est fermée
        ↓
  Action "STOP"       ← stopSelf() → onDestroy()
        ↓
onDestroy()           ← Nettoyage : arrête thread + supprime notification
```

---

## 🛠️ Technologies utilisées

| Catégorie | Technologie | Version |
|-----------|-------------|---------|
| IDE | Android Studio | Dernière |
| Langage | Java | 8+ |
| Service | Foreground Service + Bound Service | - |
| Notification | NotificationCompat | AndroidX |
| Threading | ScheduledExecutorService | Java concurrency |

---

## 📂 Structure du projet

```
ServiceChronometre/
├── java/com.example.servicechronometrejava/
│   ├── MainActivity.java           # Interface utilisateur + liaison service
│   └── ChronometreService.java     # Service (Foreground + Bound)
├── res/layout/
│   └── activity_main.xml           # Layout (TextView + 2 boutons)
├── manifests/
│   └── AndroidManifest.xml         # Permissions + déclaration service
└── build.gradle                    # Dépendances
```

---

## 📱 Fonctionnalités de l'application

| Fonctionnalité | Statut |
|----------------|--------|
| Service Foreground avec notification | ✅ |
| Bound Service (communication Activity ↔ Service) | ✅ |
| Chronomètre qui tourne en arrière-plan | ✅ |
| Mise à jour de l'UI en temps réel (Handler) | ✅ |
| Notification persistante qui affiche le temps | ✅ |
| Redémarrage auto si système tue le service (START_STICKY) | ✅ |
| Permission notifications (Android 13+) | ✅ |
| Permission Foreground Service (Android 8+) | ✅ |

---

## 💻 Extraits de code importants

### 1. Démarrage et liaison du service — `MainActivity.java`

```java
private void startChronometre() {
    Intent intent = new Intent(this, ChronometreService.class);

    // ÉTAPE 1 : Démarrer le service (Foreground)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent);
    } else {
        startService(intent);
    }

    // ÉTAPE 2 : Lier l'Activity au service (Bound Service)
    bindService(intent, connection, Context.BIND_AUTO_CREATE);
}

// ÉTAPE 3 : ServiceConnection - récupère l'instance du service
private final ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ChronometreService.LocalBinder binder = (ChronometreService.LocalBinder) service;
        chronometreService = binder.getService(); // MainActivity peut appeler les méthodes
        isBound = true;
        startUpdatingUI(); // Démarre la mise à jour du TextView
    }
};
```

### 2. Service avec notification — `ChronometreService.java`

```java
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    if ("STOP".equals(intent.getAction())) {
        stopSelf();
        return START_NOT_STICKY;
    }

    if (!isRunning) {
        isRunning = true;
        startForeground(NOTIFICATION_ID, createNotification()); // Obligatoire
        startChronometre(); // Lance le timer
    }
    return START_STICKY; // Redémarrage auto si tué par le système
}

private void startChronometre() {
    executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(() -> {
        secondes++;
        updateNotification(); // Met à jour la notification en direct
    }, 0, 1, TimeUnit.SECONDS);
}
```

### 3. Binder pour la communication

```java
public class LocalBinder extends Binder {
    public ChronometreService getService() {
        return ChronometreService.this; // Retourne l'instance du service
    }
}
```

---

## 📋 Permissions — `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<service
    android:name=".ChronometreService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

---

## 📊 Ordre d'exécution des fonctions

| Ordre | Fonction | Rôle |
|-------|----------|------|
| 1 | `onCreate()` | Initialise le service |
| 2 | `onStartCommand()` | Démarre le chrono et la notification |
| 3 | `startForeground()` | Rend le service visible |
| 4 | `startChronometre()` | Lance le timer périodique |
| 5 | `scheduleAtFixedRate()` | Incrémente secondes + met à jour notification |
| 6 | `onBind()` | Retourne le binder pour la communication |
| 7 | `onServiceConnected()` | Récupère l'instance du service |
| 8 | `getCurrentTime()` | Appelé par MainActivity pour afficher le temps |
| 9 | `onDestroy()` | Nettoie le service à l'arrêt |

---

## 📸 Captures d'écran

### Écran principal - Démarrage

<img width="350" height="720" alt="Debut_test" src="https://github.com/user-attachments/assets/cf46895f-e397-4612-99f8-bda482767fb0" />


*Application au lancement avec le chronomètre à 00:00*

---

---

### Chronomètre en marche

<img width="342" height="719" alt="Lancement_test" src="https://github.com/user-attachments/assets/b5261d7d-660f-4e7c-8e1a-307cb4c83028" />


*Le chrono tourne et affiche le temps écoulé*

---



### Code - ChronometreService.java

<img width="903" height="844" alt="Chrono1" src="https://github.com/user-attachments/assets/91c09f51-ba30-4e36-8e18-ab62d007ed13" />
<img width="956" height="836" alt="Chrono2" src="https://github.com/user-attachments/assets/f5c65e7b-8297-4cb0-b153-5bc88610d076" />
<img width="932" height="838" alt="Chrono3" src="https://github.com/user-attachments/assets/ed386ec7-962c-4442-8962-44aa79da5783" />
<img width="1025" height="797" alt="Chrono4" src="https://github.com/user-attachments/assets/7826a74f-b7d5-48b4-add8-1691f5e65262" />
<img width="878" height="424" alt="Chrono5" src="https://github.com/user-attachments/assets/21bd93a3-a512-450d-8f97-1ce96fdd00d8" />


*Structure du service avec Binder et notification*

---

### Code - MainActivity.java

<img width="913" height="834" alt="Main1" src="https://github.com/user-attachments/assets/d04754ad-4757-495c-9115-97556ce509a2" />
<img width="970" height="845" alt="Main2" src="https://github.com/user-attachments/assets/8e456596-83df-4acf-9476-611f75f1f69d" />
<img width="1087" height="852" alt="Main3" src="https://github.com/user-attachments/assets/18affdd3-3d23-402f-b624-352c2bce658d" />
<img width="949" height="832" alt="Main4" src="https://github.com/user-attachments/assets/c220b2e4-1c6e-48dc-ba42-7cf9e0a5c853" />
<img width="1077" height="528" alt="Main5" src="https://github.com/user-attachments/assets/25b52195-6672-4dbb-9489-513de326f28c" />


*Liaison du service et mise à jour de l'interface*

---

## 🎬 Vidéo de démonstration



https://github.com/user-attachments/assets/4c5a8930-9a35-4d09-bece-4f3c0eae13cc



*Vidéo montrant le lancement, le fonctionnement en arrière-plan et l'arrêt du service*





## 📚 RÉCAPITULATIF – CE QUE J'AI APPRIS

---

### ✅ Synthèse du laboratoire

Ce laboratoire m'a permis de maîtriser les **Services Android** dans leurs deux formes : **Foreground** (avec notification persistante) et **Bound** (communication bidirectionnelle avec l'Activity). J'ai également appris à gérer le **threading** avec `ScheduledExecutorService` et la mise à jour de l'UI avec `Handler + Runnable`.

---

### 📝 Les 7 points essentiels à retenir

| # | Point clé |
|---|-----------|
| 1 | **`startForeground()`** est obligatoire depuis Android 8.0 (API 26) |
| 2 | **`START_STICKY`** : le service redémarre si le système le tue |
| 3 | **`ScheduledExecutorService`** exécute la tâche périodique dans un thread séparé |
| 4 | **`LocalBinder`** permet la communication bidirectionnelle Activity ↔ Service |
| 5 | **`PendingIntent`** dans la notification permet de rouvrir l'application |
| 6 | **`ServiceConnection`** est l'interface qui gère la connexion au service |
| 7 | Les deux appels **`startService()` + `bindService()`** sont nécessaires ensemble |

---

### 📊 Comparaison : Started vs Bound vs Foreground Service

| Type | Visible | Communication | Survie si app fermée |
|------|---------|---------------|----------------------|
| **Started Service** | ❌ | ❌ | ✅ |
| **Bound Service** | ❌ | ✅ bidirectionnel | ❌ (lié à l'Activity) |
| **Foreground Service** | ✅ notification | ✅ (si aussi Bound) | ✅ |

---

### 💡 Bonnes pratiques retenues

- [x] Toujours appeler `startForeground()` dans `onStartCommand()` (pas dans `onCreate()`)
- [x] Utiliser `ScheduledExecutorService` plutôt que `Timer` pour les tâches périodiques
- [x] Nettoyer les ressources dans `onDestroy()` (arrêt executor, suppression notification)
- [x] Combiner `startService()` et `bindService()` pour avoir à la fois la persistance et la communication
- [x] Vérifier la version Android avant d'appeler `startForegroundService()`

---

### 🎯 Compétences acquises

| Compétence | Niveau |
|------------|--------|
| Créer un Foreground Service avec notification | ✅ Maîtrisé |
| Implémenter un Bound Service avec Binder | ✅ Maîtrisé |
| Utiliser ScheduledExecutorService | ✅ Maîtrisé |
| Mettre à jour l'UI depuis un Service (Handler) | ✅ Maîtrisé |
| Gérer le cycle de vie d'un Service | ✅ Maîtrisé |
| Créer un NotificationChannel (Android 8+) | ✅ Maîtrisé |

---

### ✅ Vérification finale

- [x] Le service démarre quand on clique sur **"DÉMARRER"**
- [x] La notification apparaît et affiche le temps
- [x] Le chronomètre tourne en arrière-plan
- [x] Quand on ferme l'application, le service continue
- [x] La notification est toujours visible
- [x] Quand on reclique sur l'application, le temps est correct
- [x] **"ARRÊTER"** arrête proprement le service

---

### 👨‍💻 Auteur

| Élément | Information |
|---------|-------------|
| **Nom** | El Hachimi Abdelhamid |
| **GitHub** | [abdotranscript25](https://github.com/abdotranscript25) |
| **Lab** | Programmation Mobile - Lab 16 |

---

### 📅 Version

| Élément | Information |
|---------|-------------|
| **Date** | Mai 2026 |
| **Version** | 1.0 |
| **Statut** | ✅ Finalisé |
