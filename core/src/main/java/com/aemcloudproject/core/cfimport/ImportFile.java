package com.aemcloudproject.core.cfimport;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * The workbook to import: the original rendition of a DAM asset, held in memory.
 */
public final class ImportFile {

    static final String DAM_ROOT = "/content/dam/";
    private static final String XLSX = ".xlsx";

    private final String name;
    private final String path;
    private final byte[] content;

    private ImportFile(String name, String path, byte[] content) {
        this.name = name;
        this.path = path;
        this.content = content;
    }

    /** Reads the original rendition of a DAM asset, with the permissions of the given resolver. */
    static ImportFile fromDamAsset(ResourceResolver resolver, String path) throws RepositoryException {
        if (!path.startsWith(DAM_ROOT)) {
            throw new RepositoryException("The DAM path must be under " + DAM_ROOT + ": " + path);
        }
        Resource resource = resolver.getResource(path);
        if (resource == null) {
            throw new RepositoryException("No asset found at " + path + ", or you can't read it.");
        }
        Asset asset = resource.adaptTo(Asset.class);
        if (asset == null) {
            throw new RepositoryException(path + " is not a DAM asset.");
        }
        String name = asset.getName();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(XLSX)) {
            throw new RepositoryException("Only .xlsx files can be imported, not " + name + ".");
        }
        Rendition original = asset.getOriginal();
        if (original == null) {
            throw new RepositoryException(path + " has no original rendition.");
        }
        try (InputStream stream = original.getStream()) {
            byte[] content = stream == null ? new byte[0] : stream.readAllBytes();
            if (content.length == 0) {
                throw new RepositoryException("The asset " + path + " is empty.");
            }
            return new ImportFile(name, path, content);
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + path, e);
        }
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public byte[] getContent() {
        return content;
    }
}
