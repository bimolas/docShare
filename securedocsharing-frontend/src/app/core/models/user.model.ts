export interface UserOrganizationMembershipDto {
  orgCode: string;
  orgName?: string | null;
  role?: string | null;
  status?: string | null;
}

export interface UserResponseDto {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  orgCode?: string | null;
  role: string;
  createdAt: string;
  activeOrganizations?: UserOrganizationMembershipDto[];
}

export interface UserRequestDto {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  orgCode?: string | null;
  role: string;
}

export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  orgCode?: string | null;
  role: string;
  createdAt: string;
  activeOrganizations?: UserOrganizationMembershipDto[];
}

/**
 * Utility mapper function to convert backend UserResponseDto to UI UserProfile
 */
export function mapUserResponseToProfile(dto: UserResponseDto): UserProfile {
  return {
    id: dto.id,
    email: dto.email,
    firstName: dto.firstName,
    lastName: dto.lastName,
    fullName: `${dto.firstName} ${dto.lastName}`.trim(),
    orgCode: dto.orgCode,
    role: dto.role,
    createdAt: dto.createdAt,
    activeOrganizations: dto.activeOrganizations ? [...dto.activeOrganizations] : [],
  };
}


