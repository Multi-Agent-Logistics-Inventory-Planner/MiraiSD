package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.InvitationResponseDTO;
import com.mirai.inventoryservice.models.audit.Invitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InvitationMapper {
    @Mapping(target = "invitedByEmail", source = "invitedBy.email")
    @Mapping(target = "status", source = ".", qualifiedByName = "toStatus")
    InvitationResponseDTO toResponseDTO(Invitation invitation);

    List<InvitationResponseDTO> toResponseDTOList(List<Invitation> invitations);

    @Named("toStatus")
    default String toStatus(Invitation invitation) {
        return invitation.isPending() ? "pending" : "accepted";
    }
}
