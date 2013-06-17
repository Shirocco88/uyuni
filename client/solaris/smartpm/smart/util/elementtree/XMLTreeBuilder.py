#
# ElementTree
#
# an XML tree builder
#
# history:
# 2001-10-20 fl   created
# 2002-05-01 fl   added namespace support for xmllib
# 2002-07-27 fl   require expat (1.5.2 code can use SimpleXMLTreeBuilder)
# 2002-08-17 fl   use tag/attribute name memo cache
# 2002-12-04 fl   moved XMLTreeBuilder to the ElementTree module
#
# Copyright (c) 1999-2003 by Fredrik Lundh.  All rights reserved.
#
# fredrik@pythonware.com
# http://www.pythonware.com
#
# --------------------------------------------------------------------
# The ElementTree toolkit is
#
# Copyright (c) 1999-2003 by Fredrik Lundh
#
# By obtaining, using, and/or copying this software and/or its
# associated documentation, you agree that you have read, understood,
# and will comply with the following terms and conditions:
#
# Permission to use, copy, modify, and distribute this software and
# its associated documentation for any purpose and without fee is
# hereby granted, provided that the above copyright notice appears in
# all copies, and that both that copyright notice and this permission
# notice appear in supporting documentation, and that the name of
# Secret Labs AB or the author not be used in advertising or publicity
# pertaining to distribution of the software without specific, written
# prior permission.
#
# SECRET LABS AB AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD
# TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANT-
# ABILITY AND FITNESS.  IN NO EVENT SHALL SECRET LABS AB OR THE AUTHOR
# BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY
# DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
# WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
# ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
# OF THIS SOFTWARE.
# --------------------------------------------------------------------

import ElementTree

##
# (obsolete) ElementTree builder for XML source data.
# <p>
# This class is an alias for ElementTree.XMLTreeBuilder.  New code
# should use that version instead.
#
# @see elementtree.ElementTree

class TreeBuilder(ElementTree.XMLTreeBuilder):
    pass

##
# (experimental) An alternate parser that supports manipulation of new
# elements.

class FancyTreeBuilder(TreeBuilder):

    def __init__(self, html=0):
        TreeBuilder.__init__(self, html)
        self._parser.StartElementHandler = self._start
        self._parser.EndElementHandler = self._end
        self._parser.StartNamespaceDeclHandler = self._start_ns
        self._parser.EndNamespaceDeclHandler = self._end_ns
        self.namespaces = []

    def _start(self, tag, attrib_in):
        elem = TreeBuilder._start(self, tag, attrib_in)
        self.start(elem)

    def _end(self, tag):
        elem = TreeBuilder._end(self, tag)
        self.end(elem)

    def _start_ns(self, prefix, value):
        self.namespaces.insert(0, (prefix, value))

    def _end_ns(self, prefix):
        assert self.namespaces.pop(0)[0] == prefix, "implementation confused"

    ##
    # Hook method that's called when a new element has been opened.
    # May access the 'namespaces' attribute.
    #
    # @param element The new element.

    def start(self, element):
        pass

    ##
    # Hook method that's called when a new element has been closed.
    # May access the 'namespaces' attribute.
    #
    # @param element The new element.

    def end(self, element):
        pass
