// Project and team details shown on the sign-in page and in the report.
// Fill in the group number and teammates here - nothing else needs to change.

export interface TeamMember { name: string; id: string; placeholder?: boolean }

export const PROJECT = {
  name: 'ResolveIQ',
  tagline: 'Intelligent IT service management',
  course: 'CS F213 · Object-Oriented Programming',
  term: 'Semester I, 2026–27',
  /** e.g. 'Group 14' */
  group: 'Group 62',
  groupPlaceholder: false,
  team: [
    { name: 'Moh Bhargava', id: '2024B4A70845H' },
    { name: 'Arnav Mhatre', id: '2025ADPS0135H' },
    { name: 'Dheer Mayur Thakkar', id: '2025A7PS0074H' },
    { name: 'Manan Agrawal', id: '2025A7PS0902H' },
  ] as TeamMember[],
}
