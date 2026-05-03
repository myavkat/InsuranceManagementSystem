export interface Client {
  id: number
  firstName: string
  lastName: string
  tcNo: string
  phoneNumber: string
}

export interface PaginatedClientsResponse {
  clients: Client[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface CreateClientResponse {
  success: boolean
  clientId?: number
}

export interface ClientFullDetails {
  id: number
  firstName: string
  lastName: string
  tcNo: string
  phoneNumber: string
  birthDate: string
  professionId: number
  cityId: number
}

export interface UpdateClientResponse {
  success: boolean
  error?: string
}

const maskedClients: Client[] = [
  {
    id: 1,
    firstName: 'Ahmet',
    lastName: 'Yılmaz',
    tcNo: '12******901',
    phoneNumber: '+90 555 *** 4567',
  },
  {
    id: 2,
    firstName: 'Ayşe',
    lastName: 'Demir',
    tcNo: '23******012',
    phoneNumber: '+90 555 *** 5678',
  },
  {
    id: 3,
    firstName: 'Mehmet',
    lastName: 'Kara',
    tcNo: '34******123',
    phoneNumber: '+90 555 *** 6789',
  },
  {
    id: 4,
    firstName: 'Fatma',
    lastName: 'Şahin',
    tcNo: '45******234',
    phoneNumber: '+90 555 *** 7890',
  },
  {
    id: 5,
    firstName: 'Ali',
    lastName: 'Çelik',
    tcNo: '56******345',
    phoneNumber: '+90 555 *** 8901',
  },
  {
    id: 6,
    firstName: 'Zeynep',
    lastName: 'Öztürk',
    tcNo: '67******456',
    phoneNumber: '+90 555 *** 9012',
  },
  {
    id: 7,
    firstName: 'Mustafa',
    lastName: 'Aydın',
    tcNo: '78******567',
    phoneNumber: '+90 555 *** 0123',
  },
  {
    id: 8,
    firstName: 'Emine',
    lastName: 'Yavuz',
    tcNo: '89******678',
    phoneNumber: '+90 555 *** 1234',
  },
  {
    id: 9,
    firstName: 'Hüseyin',
    lastName: 'Güler',
    tcNo: '90******789',
    phoneNumber: '+90 555 *** 2345',
  },
  {
    id: 10,
    firstName: 'Selin',
    lastName: 'Koç',
    tcNo: '01******890',
    phoneNumber: '+90 555 *** 3456',
  },
  {
    id: 11,
    firstName: 'İbrahim',
    lastName: 'Kurt',
    tcNo: '11******556',
    phoneNumber: '+90 555 *** 2222',
  },
  {
    id: 12,
    firstName: 'Merve',
    lastName: 'Aktaş',
    tcNo: '22******667',
    phoneNumber: '+90 555 *** 4444',
  },
  {
    id: 13,
    firstName: 'Kemal',
    lastName: 'Aslan',
    tcNo: '33******778',
    phoneNumber: '+90 555 *** 6666',
  },
  {
    id: 14,
    firstName: 'Gül',
    lastName: 'Karaca',
    tcNo: '44******889',
    phoneNumber: '+90 555 *** 8888',
  },
  {
    id: 15,
    firstName: 'Burak',
    lastName: 'Eren',
    tcNo: '55******990',
    phoneNumber: '+90 555 *** 0000',
  },
]

export async function getClients(
  page: number = 1,
  limit: number = 10,
): Promise<PaginatedClientsResponse> {
  await new Promise((resolve) => setTimeout(resolve, 300))

  const start = (page - 1) * limit
  const end = start + limit

  const clients = maskedClients.slice(start, end)

  return {
    clients,
    total: maskedClients.length,
    page,
    limit,
    totalPages: Math.ceil(maskedClients.length / limit),
  }
}

export async function createClient(_client: Omit<Client, 'id'>): Promise<CreateClientResponse> {
  await new Promise((resolve) => setTimeout(resolve, 500))

  const clientId = Math.floor(Math.random() * 10000) + 1

  return {
    success: true,
    clientId,
  }
}

export async function updateClient(
  _id: number,
  _client: Partial<ClientFullDetails>,
): Promise<UpdateClientResponse> {
  await new Promise((resolve) => setTimeout(resolve, 500))

  return {
    success: true,
  }
}

const clientDetailData: Record<number, ClientFullDetails> = {
  1: {
    id: 1,
    firstName: 'Ahmet',
    lastName: 'Yılmaz',
    tcNo: '12345678901',
    phoneNumber: '+90 555 123 4567',
    birthDate: '1985-03-15',
    professionId: 1,
    cityId: 7,
  },
  2: {
    id: 2,
    firstName: 'Ayşe',
    lastName: 'Demir',
    tcNo: '23456789012',
    phoneNumber: '+90 555 234 5678',
    birthDate: '1990-07-22',
    professionId: 2,
    cityId: 34,
  },
  3: {
    id: 3,
    firstName: 'Mehmet',
    lastName: 'Kara',
    tcNo: '34567890123',
    phoneNumber: '+90 555 345 6789',
    birthDate: '1978-11-05',
    professionId: 5,
    cityId: 16,
  },
  4: {
    id: 4,
    firstName: 'Fatma',
    lastName: 'Şahin',
    tcNo: '45678901234',
    phoneNumber: '+90 555 456 7890',
    birthDate: '1992-01-30',
    professionId: 3,
    cityId: 6,
  },
  5: {
    id: 5,
    firstName: 'Ali',
    lastName: 'Çelik',
    tcNo: '56789012345',
    phoneNumber: '+90 555 567 8901',
    birthDate: '1988-09-12',
    professionId: 1,
    cityId: 41,
  },
}

export async function getClientDetail(id: number): Promise<ClientFullDetails | null> {
  await new Promise((resolve) => setTimeout(resolve, 300))

  return clientDetailData[id] || null
}
