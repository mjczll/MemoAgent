export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export class ApiError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...init.headers,
      },
    });
  } catch {
    throw new ApiError(0, "无法连接后端，请确认服务已启动");
  }

  const raw = await response.text();
  if (!raw) {
    if (!response.ok) {
      throw new ApiError(response.status, "请求失败");
    }
    return undefined as T;
  }

  let body: ApiResult<T>;
  try {
    body = JSON.parse(raw) as ApiResult<T>;
  } catch {
    throw new ApiError(response.status, `服务器返回了无法解析的响应（HTTP ${response.status}）`);
  }

  if (body.code !== 200) {
    throw new ApiError(body.code, body.message || "请求失败");
  }
  return body.data;
}

export function errorMessage(error: unknown, fallback = "请求失败"): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
