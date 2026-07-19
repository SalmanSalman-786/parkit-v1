package com.parking.backend.service;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadImage(MultipartFile file) throws IOException {

        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                Map.of(
                        "folder", "parkit"
                ));

        return result.get("secure_url").toString();
    }

    public void deleteImage(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, Map.of());
    }
}