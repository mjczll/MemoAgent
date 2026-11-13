/** 筛选控件的通用选项结构，FilterSegment / FilterSelect / TagChips 共用。 */
export interface FilterOption {
  value: string;
  label: string;
  count?: number;
}
