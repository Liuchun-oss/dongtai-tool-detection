#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
将Markdown格式的项目汇报文档转换为Word文档
"""

import re
from docx import Document
from docx.shared import Pt, RGBColor, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn

def parse_markdown_to_word(md_file, docx_file):
    """将Markdown文件转换为Word文档"""
    
    # 创建Word文档
    doc = Document()
    
    # 设置中文字体
    def set_chinese_font(run):
        run.font.name = '宋体'
        run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')
    
    # 读取Markdown文件
    with open(md_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        
        # 跳过空行
        if not line:
            i += 1
            continue
        
        # 处理标题
        if line.startswith('# '):
            # 一级标题
            title = line[2:].strip()
            p = doc.add_heading(title, level=1)
            for run in p.runs:
                set_chinese_font(run)
                run.font.size = Pt(22)
                run.font.bold = True
        elif line.startswith('## '):
            # 二级标题
            title = line[3:].strip()
            p = doc.add_heading(title, level=2)
            for run in p.runs:
                set_chinese_font(run)
                run.font.size = Pt(18)
                run.font.bold = True
        elif line.startswith('### '):
            # 三级标题
            title = line[4:].strip()
            p = doc.add_heading(title, level=3)
            for run in p.runs:
                set_chinese_font(run)
                run.font.size = Pt(16)
                run.font.bold = True
        elif line.startswith('#### '):
            # 四级标题
            title = line[5:].strip()
            p = doc.add_heading(title, level=4)
            for run in p.runs:
                set_chinese_font(run)
                run.font.size = Pt(14)
                run.font.bold = True
        elif line.startswith('---'):
            # 分隔线，跳过
            pass
        elif line.startswith('|'):
            # 表格
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                table_lines.append(lines[i].strip())
                i += 1
            i -= 1  # 回退一行
            
            if len(table_lines) >= 2:
                # 解析表格
                headers = [cell.strip() for cell in table_lines[0].split('|')[1:-1]]
                # 跳过分隔行
                data_rows = []
                for row_line in table_lines[2:]:
                    cells = [cell.strip() for cell in row_line.split('|')[1:-1]]
                    if cells:
                        data_rows.append(cells)
                
                # 创建Word表格
                table = doc.add_table(rows=1, cols=len(headers))
                table.style = 'Light Grid Accent 1'
                
                # 添加表头
                header_cells = table.rows[0].cells
                for j, header in enumerate(headers):
                    header_cells[j].text = header
                    for run in header_cells[j].paragraphs[0].runs:
                        set_chinese_font(run)
                        run.font.bold = True
                        run.font.size = Pt(11)
                
                # 添加数据行
                for row_data in data_rows:
                    row_cells = table.add_row().cells
                    for j, cell_data in enumerate(row_data):
                        if j < len(row_cells):
                            row_cells[j].text = cell_data
                            for run in row_cells[j].paragraphs[0].runs:
                                set_chinese_font(run)
                                run.font.size = Pt(10)
        elif line.startswith('```'):
            # 代码块
            code_lines = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith('```'):
                code_lines.append(lines[i])
                i += 1
            
            # 添加代码块内容
            p = doc.add_paragraph()
            p.style = 'No Spacing'
            run = p.add_run('\n'.join(code_lines))
            set_chinese_font(run)
            run.font.name = 'Consolas'
            run.font.size = Pt(10)
            run.font.color.rgb = RGBColor(0, 0, 0)
            # 设置段落背景色（浅灰色）
            p.paragraph_format.left_indent = Inches(0.5)
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(6)
        elif line.startswith('- ') or line.startswith('* '):
            # 无序列表
            list_text = line[2:].strip()
            # 处理加粗和特殊符号
            list_text = re.sub(r'✅', '✓', list_text)  # 替换勾选符号
            
            p = doc.add_paragraph(style='List Bullet')
            # 处理加粗文本
            parts = re.split(r'(\*\*.*?\*\*)', list_text)
            for part in parts:
                if part.startswith('**') and part.endswith('**'):
                    # 加粗文本
                    run = p.add_run(part[2:-2])
                    set_chinese_font(run)
                    run.font.size = Pt(12)
                    run.font.bold = True
                elif part:
                    # 普通文本
                    run = p.add_run(part)
                    set_chinese_font(run)
                    run.font.size = Pt(12)
        elif re.match(r'^\d+\.\s', line):
            # 有序列表
            list_text = re.sub(r'^\d+\.\s', '', line)
            
            p = doc.add_paragraph(style='List Number')
            # 处理加粗文本
            parts = re.split(r'(\*\*.*?\*\*)', list_text)
            for part in parts:
                if part.startswith('**') and part.endswith('**'):
                    # 加粗文本
                    run = p.add_run(part[2:-2])
                    set_chinese_font(run)
                    run.font.size = Pt(12)
                    run.font.bold = True
                elif part:
                    # 普通文本
                    run = p.add_run(part)
                    set_chinese_font(run)
                    run.font.size = Pt(12)
        else:
            # 普通段落
            para_text = line
            para_text = re.sub(r'✅', '✓', para_text)
            para_text = re.sub(r'⭐', '★', para_text)
            
            p = doc.add_paragraph()
            # 处理加粗文本
            parts = re.split(r'(\*\*.*?\*\*)', para_text)
            for part in parts:
                if part.startswith('**') and part.endswith('**'):
                    # 加粗文本
                    run = p.add_run(part[2:-2])
                    set_chinese_font(run)
                    run.font.size = Pt(12)
                    run.font.bold = True
                elif part:
                    # 普通文本
                    run = p.add_run(part)
                    set_chinese_font(run)
                    run.font.size = Pt(12)
        
        i += 1
    
    # 保存文档
    doc.save(docx_file)
    print(f"Word文档已成功生成: {docx_file}")

if __name__ == '__main__':
    md_file = '项目汇报文档.md'
    docx_file = '项目汇报文档.docx'
    
    try:
        parse_markdown_to_word(md_file, docx_file)
    except ImportError:
        print("错误: 需要安装 python-docx 库")
        print("请运行: pip install python-docx")
    except Exception as e:
        print(f"转换失败: {e}")
        import traceback
        traceback.print_exc()
