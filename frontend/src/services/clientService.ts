export interface Client {
  firstName: string
  lastName: string
  tcNo: string
  phoneNumber: string
  birthDate: string
  professionId: number
  cityId: number
}

export interface CreateClientResponse {
  success: boolean
  clientId?: number
}

export async function createClient(_client: Client): Promise<CreateClientResponse> {
  await new Promise((resolve) => setTimeout(resolve, 500))

  const clientId = Math.floor(Math.random() * 10000) + 1

  return {
    success: true,
    clientId,
  }
}