package com.b2bfintech.app.service;

import com.b2bfintech.common.document.ClientCodeResolver;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.repository.ClientRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Upload folder is the client's code, taken from the signed-in client user. */
@Component
public class ClientCodeFolderResolver implements ClientCodeResolver {

    private final ClientRepository clientRepository;

    public ClientCodeFolderResolver(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public String resolve(AuthenticatedUser principal) {
        if (principal == null || principal.clientId() == null || principal.clientId().isBlank()) {
            throw new BadRequestException("A client session is required to upload a file");
        }
        UUID clientId;
        try {
            clientId = UUID.fromString(principal.clientId());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("A client session is required to upload a file");
        }
        String code = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"))
                .getClientCode();
        if (code == null || code.isBlank()) {
            throw new BadRequestException("This client does not have a client code yet");
        }
        return code;
    }
}
