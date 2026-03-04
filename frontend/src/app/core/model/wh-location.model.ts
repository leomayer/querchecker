export interface WhLocationDto {
  areaId: number;
  name: string;
  level: number;
  children: WhLocationDto[];
}
