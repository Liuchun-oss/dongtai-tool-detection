#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
读取用户修改后的Word文档，添加致谢领导和征求意见的内容
"""

import os
from docx import Document
from docx.shared import Pt, RGBColor, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn

def set_chinese_font(run):
    """设置中文字体"""
    run.font.name = '宋体'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '宋体')

def add_leadership_section(doc):
    """添加致谢领导和征求意见的内容"""
    
    # 添加分隔段落
    doc.add_paragraph()
    
    # 添加"八、致谢与领导指导"章节
    heading = doc.add_heading('八、致谢与领导指导', level=2)
    for run in heading.runs:
        set_chinese_font(run)
        run.font.size = Pt(18)
        run.font.bold = True
    
    # 8.1 领导支持与指导
    sub_heading1 = doc.add_heading('8.1 领导支持与指导', level=3)
    for run in sub_heading1.runs:
        set_chinese_font(run)
        run.font.size = Pt(16)
        run.font.bold = True
    
    # 添加领导支持的内容
    leadership_content = [
        "本项目能够顺利开展并取得预期成果，离不开各级领导的关心、支持和悉心指导。",
        "",
        "在项目立项阶段，领导高瞻远瞩，充分认识到AI技术在提升铁路作业安全方面的重要价值，给予了项目立项的坚定支持，为项目的启动奠定了坚实基础。",
        "",
        "在项目实施过程中，领导多次深入项目现场，了解项目进展，协调解决项目推进中遇到的各种困难和问题，为项目团队提供了强有力的组织保障。",
        "",
        "在技术攻关阶段，领导从战略高度为项目指明了方向，提出了许多建设性的意见和建议，特别是在AI模型优化、用户体验提升等方面给予了宝贵的指导，使项目能够更好地服务于实际业务需求。",
        "",
        "在资源保障方面，领导积极协调各方资源，为项目提供了必要的硬件设备、数据支持和人员配置，确保了项目的顺利推进。"
    ]
    
    for content in leadership_content:
        if content:
            p = doc.add_paragraph(content)
            for run in p.runs:
                set_chinese_font(run)
                run.font.size = Pt(12)
        else:
            doc.add_paragraph()  # 空行
    
    # 8.2 领导贡献
    sub_heading2 = doc.add_heading('8.2 领导贡献', level=3)
    for run in sub_heading2.runs:
        set_chinese_font(run)
        run.font.size = Pt(16)
        run.font.bold = True
    
    contribution_content = [
        "领导在本项目中发挥了关键作用，主要体现在以下几个方面：",
        "",
        "1. **战略指导**：从全局高度为项目定位，明确了项目的目标和价值，为项目团队指明了前进方向。",
        "",
        "2. **资源协调**：积极协调各方资源，为项目提供了必要的支持和保障，确保项目能够顺利推进。",
        "",
        "3. **决策支持**：在关键技术选型、实施方案确定等关键节点，领导给予了及时、准确的决策支持。",
        "",
        "4. **质量把关**：对项目成果进行严格把关，确保项目质量符合预期要求，为项目的成功实施提供了重要保障。",
        "",
        "5. **创新推动**：鼓励项目团队大胆创新，勇于尝试新技术、新方法，为项目的技术创新提供了良好的氛围。"
    ]
    
    for content in contribution_content:
        if content:
            p = doc.add_paragraph()
            # 处理加粗文本
            parts = content.split('**')
            for i, part in enumerate(parts):
                if part:
                    run = p.add_run(part)
                    set_chinese_font(run)
                    run.font.size = Pt(12)
                    if i % 2 == 1:  # 奇数索引是加粗内容
                        run.font.bold = True
        else:
            doc.add_paragraph()  # 空行
    
    # 8.3 恳请领导指导
    sub_heading3 = doc.add_heading('8.3 恳请领导指导', level=3)
    for run in sub_heading3.runs:
        set_chinese_font(run)
        run.font.size = Pt(16)
        run.font.bold = True
    
    guidance_content = [
        "本项目虽然取得了一定的成果，但在技术实现、功能完善、推广应用等方面仍有进一步提升的空间。",
        "",
        "我们深知，领导具有丰富的管理经验和敏锐的行业洞察力，能够从更高的视角审视项目，发现我们可能忽视的问题，提出更具前瞻性的建议。",
        "",
        "因此，我们诚恳地希望领导能够：",
        "",
        "1. **提出宝贵意见**：对项目的技术方案、功能设计、用户体验等方面提出指导性意见，帮助我们进一步完善系统。",
        "",
        "2. **指出改进方向**：从业务需求、管理规范、安全标准等角度，指出项目需要改进和优化的方向。",
        "",
        "3. **分享行业经验**：分享在类似项目中的成功经验和最佳实践，为项目的后续发展提供参考。",
        "",
        "4. **指导推广应用**：对项目的推广应用策略、培训方案、运维保障等方面给予指导，确保项目能够真正落地见效。",
        "",
        "我们团队将认真听取领导的意见和建议，积极改进和完善项目，努力将本项目打造成一个真正实用、好用、管用的智能工具清点系统，为铁路作业安全贡献更大的力量。",
        "",
        "再次感谢领导对本项目的关心、支持和指导！"
    ]
    
    for content in guidance_content:
        if content:
            p = doc.add_paragraph()
            # 处理加粗文本
            parts = content.split('**')
            for i, part in enumerate(parts):
                if part:
                    run = p.add_run(part)
                    set_chinese_font(run)
                    run.font.size = Pt(12)
                    if i % 2 == 1:  # 奇数索引是加粗内容
                        run.font.bold = True
        else:
            doc.add_paragraph()  # 空行

def update_word_document(docx_file):
    """更新Word文档，添加致谢领导和征求意见的内容"""
    
    try:
        # 读取现有的Word文档
        doc = Document(docx_file)
        
        print(f"正在读取文档: {docx_file}")
        print(f"文档当前段落数: {len(doc.paragraphs)}")
        
        # 检查是否已经添加过相关内容
        existing_text = '\n'.join([p.text for p in doc.paragraphs])
        if '致谢与领导指导' in existing_text:
            print("文档中已存在致谢内容，将在末尾追加新内容...")
            # 如果已存在，在末尾添加
            pass
        else:
            print("添加新的致谢和征求意见章节...")
        
        # 添加致谢领导和征求意见的内容
        add_leadership_section(doc)
        
        # 保存文档（创建新版本，保留原文件）
        output_file = docx_file.replace('.docx', '_含致谢版.docx')
        try:
            doc.save(output_file)
            print(f"文档已更新并保存为: {output_file}")
            print(f"原文件保持不变: {docx_file}")
            print("\n请关闭Word后，我可以直接更新原文件")
        except PermissionError:
            print(f"文件被占用，已保存为新文件: {output_file}")
            print("请关闭Word后运行脚本，或手动将新文件内容复制到原文件")
        
        return output_file
        
    except FileNotFoundError:
        print(f"错误: 找不到文件 {docx_file}")
        print("请确保文件存在，或提供正确的文件路径")
        return None
    except Exception as e:
        print(f"更新文档时出错: {e}")
        import traceback
        traceback.print_exc()
        return None

if __name__ == '__main__':
    # 尝试多个可能的文件路径
    possible_files = [
        '项目汇报文档.docx',
        os.path.join(os.getcwd(), '项目汇报文档.docx'),
    ]
    
    docx_file = None
    for file_path in possible_files:
        if os.path.exists(file_path):
            docx_file = file_path
            break
    
    if docx_file:
        print(f"找到文件: {docx_file}")
        update_word_document(docx_file)
    else:
        print("未找到Word文档文件")
        print("请确保文件 '项目汇报文档.docx' 存在于当前目录")
        print(f"当前目录: {os.getcwd()}")
        print("\n可用的.docx文件:")
        for root, dirs, files in os.walk('.'):
            for file in files:
                if file.endswith('.docx'):
                    print(f"  - {os.path.join(root, file)}")
