// Project and team details shown on the sign-in page and in the report.
// Fill in the group number and teammates here - nothing else needs to change.

export interface TeamMember { name: string; id: string; placeholder?: boolean }

export const PROJECT = {
  name: 'ResolveIQ',
  tagline: 'Intelligent IT service management',
  course: 'CS F213 · Object-Oriented Programming',
  term: 'Semester I, 2026–27',
  /** e.g. 'Group 14' */
  group: 'Group —',
  groupPlaceholder: true,
  team: [
    { name: 'Moh Bhargava', id: '2024B4A70845H' },
    { name: 'Team member 2', id: 'ID number', placeholder: true },
    { name: 'Team member 3', id: 'ID number', placeholder: true },
    { name: 'Team member 4', id: 'ID number', placeholder: true },
  ] as TeamMember[],
}
