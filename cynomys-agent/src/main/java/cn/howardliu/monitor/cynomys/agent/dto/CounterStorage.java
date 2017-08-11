/*
 * Copyright 2008-2016 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.howardliu.monitor.cynomys.agent.dto;

import cn.howardliu.monitor.cynomys.agent.conf.Parameter;
import cn.howardliu.monitor.cynomys.agent.conf.Parameters;
import cn.howardliu.monitor.cynomys.agent.handler.wrapper.CounterResponseStream;
import cn.howardliu.monitor.cynomys.agent.util.TransportFormat;

import java.io.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Classe chargée de l'enregistrement et de la lecture d'un counter.
 *
 * @author Emeric Vernat
 */
public class CounterStorage {
    private static final int DEFAULT_OBSOLETE_STATS_DAYS = 365;
    private static boolean storageDisabled;
    private final Counter counter;

    /**
     * Constructeur.
     *
     * @param counter Counter
     */
    public CounterStorage(Counter counter) {
        super();
        assert counter != null;
        this.counter = counter;
    }

    /**
     * Enregistre le counter.
     *
     * @return Taille sérialisée non compressée du counter (estimation
     * pessimiste de l'occupation mémoire)
     * @throws java.io.IOException Exception d'entrée/sortie
     */
    int writeToFile() throws IOException {
        if (storageDisabled) {
            return -1;
        }
        final File file = getFile();
        if (counter.getRequestsCount() == 0 && counter.getErrorsCount() == 0 && !file.exists()) {
            // s'il n'y a pas de requête, inutile d'écrire des fichiers de
            // compteurs vides
            // (par exemple pour le compteur ejb s'il n'y a pas d'ejb)
            return -1;
        }
        final File directory = file.getParentFile();
        if (!directory.mkdirs() && !directory.exists()) {
            throw new IOException("WFJ-Netty-Monitor directory can't be created: " + directory.getPath());
        }
        final FileOutputStream out = new FileOutputStream(file);
        try {
            final CounterResponseStream counterOutput = new CounterResponseStream(
                    new GZIPOutputStream(new BufferedOutputStream(out)));
            try (ObjectOutputStream output = new ObjectOutputStream(counterOutput)) {
                output.writeObject(counter);
            }
            // ce close libère les ressources du ObjectOutputStream et du
            // GZIPOutputStream

            // retourne la taille sérialisée non compressée,
            // qui est une estimation pessimiste de l'occupation mémoire
            return counterOutput.getDataLength();
        } finally {
            out.close();
        }
    }

    /**
     * Lecture du counter depuis son fichier et retour du résultat.
     *
     * @return Counter
     * @throws java.io.IOException e
     */
    public Counter readFromFile() throws IOException {
        if (storageDisabled) {
            return null;
        }
        final File file = getFile();
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                final ObjectInputStream input = TransportFormat
                        .createObjectInputStream(new GZIPInputStream(new BufferedInputStream(in)));
                try {
                    // on retourne l'instance du counter lue
                    return (Counter) input.readObject();
                } finally {
                    // ce close libère les ressources du ObjectInputStream et du
                    // GZIPInputStream
                    input.close();
                }
            } catch (final ClassNotFoundException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
        // ou on retourne null si le fichier n'existe pas
        return null;
    }

    private File getFile() {
        final File storageDirectory = Parameters.getStorageDirectory(counter.getApplication());
        return new File(storageDirectory, counter.getStorageName() + ".ser.gz");
    }

    static long deleteObsoleteCounterFiles(String application) {
        final Calendar nowMinusOneYearAndADay = Calendar.getInstance();
        nowMinusOneYearAndADay.add(Calendar.DAY_OF_YEAR, -getObsoleteStatsDays());
        nowMinusOneYearAndADay.add(Calendar.DAY_OF_YEAR, -1);
        // filtre pour ne garder que les fichiers d'extension .ser.gz et pour
        // éviter d'instancier des File inutiles
        long diskUsage = 0;
        for (final File file : listSerGzFiles(application)) {
            boolean deleted = false;
            if (file.lastModified() < nowMinusOneYearAndADay.getTimeInMillis()) {
                deleted = file.delete();
            }
            if (!deleted) {
                diskUsage += file.length();
            }
        }

        // on retourne true si tous les fichiers .ser.gz obsolètes ont été
        // supprimés, false sinon
        return diskUsage;
    }

    /**
     * @return Nombre de jours avant qu'un fichier de statistiques (extension
     * .ser.gz), soit considéré comme obsolète et soit supprimé
     * automatiquement, à minuit (365 par défaut, soit 1 an)
     */
    private static int getObsoleteStatsDays() {
        final String param = Parameters.getParameter(Parameter.OBSOLETE_STATS_DAYS);
        if (param != null) {
            // lance une NumberFormatException si ce n'est pas un nombre
            final int result = Integer.parseInt(param);
            if (result <= 0) {
                throw new IllegalStateException("The parameter obsolete-stats-days should be > 0 (365 recommended)");
            }
            return result;
        }
        return DEFAULT_OBSOLETE_STATS_DAYS;
    }

    private static List<File> listSerGzFiles(String application) {
        final File storageDir = Parameters.getStorageDirectory(application);
        // filtre pour ne garder que les fichiers d'extension .rrd et pour
        // éviter d'instancier des File inutiles
        final FilenameFilter filenameFilter = new FilenameFilter() {
            /** {@inheritDoc} */
            @Override
            public boolean accept(File dir, String fileName) {
                return fileName.endsWith(".ser.gz");
            }
        };
        final File[] files = storageDir.listFiles(filenameFilter);
        if (files == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(files);
    }

    // cette méthode est utilisée dans l'ihm Swing
    static void disableStorage() {
        storageDisabled = true;
    }
}