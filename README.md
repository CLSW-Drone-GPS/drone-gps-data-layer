# Projet telephone & smartwatch : DRONE MAP

Projet tenu par Antoine Vidal-Mazuy, Loic Madern & Antoine Huot-Marchand

## Afin de lancer le projet, il est réaliser les étapes ci-dessous :

- Activer la clé API Google Maps SDK for Android, attention à bien lié votre clé avec le nom du package : com.clsw.drone
  
Voici un lien pour y accéder facilement : https://console.cloud.google.com/apis/library?project=poetic-emblem-368716

Et vous pouvez aussi suivre le début de ce tuto au cas où : https://developers.google.com/maps/documentation/android-sdk/cloud-setup?hl=fr 

- Cette clé est à remplacer dans le fichier AndroidManifest.xml : 

 ```
<meta-data
android:name="com.google.android.geo.API_KEY"
android:value="***Votre clé***" />
```

Cela ne fonctionne pas toujours mais il est préférable de mettre votre clé dans une variable dans le fichier local.properties

- Il est nécessaire d'avoir une montre andoid connectée à un téléphone, et que les deux applications soient connectées. Une fois installées, il faut lancer les deux applications et suivre les étapes de connection respectives des deux applications.




