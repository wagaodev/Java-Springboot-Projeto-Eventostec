package com.eventostec.api.service;

import com.amazonaws.services.s3.AmazonS3;
import com.eventostec.api.domain.event.Event;
import com.eventostec.api.domain.event.EventRequestDTO;
import com.eventostec.api.repositories.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class EventService {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    private final String bucketName;
    private final AmazonS3 s3Client;
    private final EventRepository repository;

    public EventService(@Value("${aws.bucket.name}") String bucketName, AmazonS3 s3Client, EventRepository repository) {
        this.bucketName = bucketName;
        this.s3Client = s3Client;
        this.repository = repository;
    }

    public Event createEvent(EventRequestDTO data) {
        String imgUrl = null;

        if (data.image() != null) {
            imgUrl = this.uploadImg(data.image());
        }

        Event newEvent = new Event();
        newEvent.setTitle(data.title());
        newEvent.setDescription(data.description());
        newEvent.setEventUrl(data.eventUrl());
        newEvent.setDate(new Date(data.date()));
        newEvent.setImgUrl(imgUrl);
        newEvent.setRemote(data.remote());
        repository.save(newEvent);

        return newEvent;
    }

    private String generateFilename(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("O arquivo Multipart não pode ser nulo ou vazio.");
        }
        return UUID.randomUUID().toString() + "-" + Objects.requireNonNull(multipartFile.getOriginalFilename());
    }

    private String uploadImg(MultipartFile multipartFile) {
        File file = null;
        String filename = generateFilename(multipartFile);

        try {
            file = this.convertMultipartToFile(multipartFile);
            s3Client.putObject(bucketName, filename, file);
            return s3Client.getUrl(bucketName, filename).toString();
        } catch (Exception e) {
            logger.error("Erro no uploadImg: {}", e.getMessage(), e);
            return "";
        } finally {
            deleteTempFile(file);
        }
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.warn("Erro ao deletar o arquivo temporário: {}", file.getAbsolutePath(), e);
            }
        }
    }



    private File convertMultipartToFile(MultipartFile multipartFile) throws IOException {
        String filename = generateFilename(multipartFile);
        File convFile = new File(System.getProperty("java.io.tmpdir"), filename);
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(multipartFile.getBytes());
        }

        return convFile;
    }
}
