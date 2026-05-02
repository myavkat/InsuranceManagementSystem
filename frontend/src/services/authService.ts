export interface LoginResponse {
  token: string
}

export async function login(_email: string, _password: string): Promise<LoginResponse> {
  await new Promise((resolve) => setTimeout(resolve, 500))

  return {
    token:
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFkbWluIFVzZXIiLCJpYXQiOjE3MDY3OTIwMDAsImV4cCI6MTcwNjc5NTYwMH0.dummy_signature',
  }
}

export async function validateToken(_token: string): Promise<boolean> {
  await new Promise((resolve) => setTimeout(resolve, 200))
  if (_token == 'hacked_token') {
    return false
  }
  return true
}
