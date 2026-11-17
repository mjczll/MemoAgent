import { request } from "./http";
import type { ProfileSettings } from "@/types";

/** 个人偏好：阶段 8.2，设置页读写迁到后端 */
export function getProfile(): Promise<ProfileSettings> {
  return request<ProfileSettings>("/api/profile");
}

export function saveProfileApi(profile: ProfileSettings): Promise<ProfileSettings> {
  return request<ProfileSettings>("/api/profile", {
    method: "PUT",
    body: JSON.stringify(profile),
  });
}
