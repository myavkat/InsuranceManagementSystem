export interface Profession {
  id: number
  name: string
}

export async function getProfessions(): Promise<Profession[]> {
  await new Promise((resolve) => setTimeout(resolve, 300))

  return [
    { id: 1, name: 'Software Developer' },
    { id: 2, name: 'Teacher' },
    { id: 3, name: 'Doctor' },
    { id: 4, name: 'Nurse' },
    { id: 5, name: 'Engineer' },
    { id: 6, name: 'Lawyer' },
    { id: 7, name: 'Accountant' },
    { id: 8, name: 'Architect' },
    { id: 9, name: 'Designer' },
    { id: 10, name: 'Marketing Manager' },
    { id: 11, name: 'Sales Representative' },
    { id: 12, name: 'Chef' },
    { id: 13, name: 'Driver' },
    { id: 14, name: 'Farmer' },
    { id: 15, name: 'Retired' },
    { id: 16, name: 'Unemployed' },
    { id: 17, name: 'Student' },
    { id: 18, name: 'Other' },
  ]
}