#!/usr/bin/env python3
from sys import argv


grammar = \
"""
Binary : Expr left, Token operator, Expr right;
Grouping : Expr expression;
Literal : Object value;
Unary : Token operator, Expr right;
"""

path_name = "../lox/Expr.java"
base_name = path_name.split('/')[-1].replace('.java', '')

def main():
    lines = grammar.replace('\n', '').strip().split(';')
    del lines[-1] # remove trailing whitespace

    header = [
        'package com.craftinginterpreters.lox;\n\n'
        'import java.util.List;\n\n'
        f'class {base_name} {{\n'
    ]

    body = []
    
    for line in lines:
        name, fields = line.split(' : ')

        body.append(f'\tstatic class {name} extends {base_name} {{\n')

        # Class constructor
        body.append(f'\t\tpublic {name}({fields}) {{\n')

        for field in fields.split(', '):
            field_name = field.split(' ')[1]

            body.append(f'\t\t\tthis.{field_name} = {field_name};\n')

        # End of constructor
        body.append('\t\t}\n')

        # Class fields
        for field in fields.split(', '):
            body.append(f'\t\tpublic {field};\n')

        # End of class
        body.append('\t}\n')


    footer = [
        '}'
    ]

    with open(path_name, 'w') as file:
        file.writelines(header)
        file.writelines(body)
        file.writelines(footer)

if __name__ == '__main__':
    if (len(argv) > 1):
        filename = argv[1]
    main()